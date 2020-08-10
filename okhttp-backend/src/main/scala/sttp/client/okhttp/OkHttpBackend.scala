package sttp.client.okhttp

import java.io.{ByteArrayInputStream, IOException, InputStream, UnsupportedEncodingException}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import okhttp3.internal.http.HttpMethod
import okhttp3.{
  Authenticator,
  Call,
  Callback,
  Credentials,
  OkHttpClient,
  Route,
  WebSocketListener,
  Request => OkHttpRequest,
  RequestBody => OkHttpRequestBody,
  Response => OkHttpResponse,
  WebSocket => OkHttpWebSocket
}
import okio.ByteString
import sttp.client.SttpBackendOptions.Proxy
import sttp.client.SttpClientException.ReadException
import sttp.client.internal.NoStreams
import sttp.client.monad.syntax._
import sttp.client.monad._
import sttp.client.okhttp.OkHttpBackend.EncodingHandler
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.{AsyncQueue, FutureAsyncQueue, SyncQueue, WebSocketEvent}
import sttp.client.{Response, ResponseAs, SttpBackend, SttpBackendOptions, _}
import sttp.model._
import sttp.model.ws.WebSocketFrame

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

abstract class OkHttpBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends SttpBackend[F, P] {

  val streams: Streams[S]
  type PE = P with Effect[F]

  private[okhttp] def convertRequest[T, R >: PE](request: Request[T, R]): OkHttpRequest = {
    val builder = new OkHttpRequest.Builder()
      .url(request.uri.toString)

    val body = bodyToOkHttp(request.body, request.headers.find(_.is(HeaderNames.ContentType)).map(_.value))
    builder.method(
      request.method.method,
      body.getOrElse {
        if (HttpMethod.requiresRequestBody(request.method.method))
          OkHttpRequestBody.create("", null)
        else null
      }
    )

    request.headers
      .foreach {
        case Header(name, value) => builder.addHeader(name, value)
      }

    builder.build()
  }

  protected val bodyToOkHttp: BodyToOkHttp[F, S]
  protected val bodyFromOkHttp: BodyFromOkHttp[F, S]

  private[okhttp] def readResponse[T, R >: PE](
      res: OkHttpResponse,
      responseAs: ResponseAs[T, R]
  ): F[Response[T]] = {
    val headers = readHeaders(res)
    val responseMetadata = ResponseMetadata(headers, StatusCode(res.code()), res.message())
    val encoding = headers.collectFirst { case h if h.is(HeaderNames.ContentEncoding) => h.value }
    val method = Method(res.request().method())
    val byteBody = if (method != Method.HEAD) {
      encoding
        .map(e =>
          customEncodingHandler //There is no PartialFunction.fromFunction in scala 2.12
            .orElse(EncodingHandler(standardEncoding))(res.body().byteStream() -> e)
        )
        .getOrElse(res.body().byteStream())
    } else {
      res.body().byteStream()
    }

    val body = bodyFromOkHttp(byteBody, responseAs, responseMetadata, None)
    responseMonad.map(body)(Response(_, StatusCode(res.code()), res.message(), headers, Nil))
  }

  private def readHeaders[R >: PE, T](res: OkHttpResponse) = {
    res
      .headers()
      .names()
      .asScala
      .flatMap(name => res.headers().values(name).asScala.map(Header(name, _)))
      .toList
  }

  private def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  override def close(): F[Unit] =
    if (closeClient) {
      responseMonad.eval(client.dispatcher().executorService().shutdown())
    } else responseMonad.unit(())

  protected def createAsyncQueue[T]: F[AsyncQueue[F, T]]

}

object OkHttpBackend {
  val DefaultWebSocketBufferCapacity: Option[Int] = Some(1024)
  type EncodingHandler = PartialFunction[(InputStream, String), InputStream]

  object EncodingHandler {
    def apply(f: (InputStream, String) => InputStream): EncodingHandler = { case (i, s) => f(i, s) }
  }

  private class ProxyAuthenticator(auth: SttpBackendOptions.ProxyAuth) extends Authenticator {
    override def authenticate(route: Route, response: OkHttpResponse): OkHttpRequest = {
      val credential = Credentials.basic(auth.username, auth.password)
      response.request.newBuilder.header("Proxy-Authorization", credential).build
    }
  }

  private[okhttp] def defaultClient(readTimeout: Long, options: SttpBackendOptions): OkHttpClient = {
    var clientBuilder = new OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(options.connectionTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeout, TimeUnit.MILLISECONDS)

    clientBuilder = options.proxy match {
      case None => clientBuilder
      case Some(p @ Proxy(_, _, _, _, Some(auth))) =>
        clientBuilder.proxySelector(p.asJavaProxySelector).proxyAuthenticator(new ProxyAuthenticator(auth))
      case Some(p) => clientBuilder.proxySelector(p.asJavaProxySelector)
    }

    clientBuilder.build()
  }

  private[okhttp] def updateClientIfCustomReadTimeout[T, S](r: Request[T, S], client: OkHttpClient): OkHttpClient = {
    val readTimeout = r.options.readTimeout
    if (readTimeout == DefaultReadTimeout) client
    else
      client
        .newBuilder()
        .readTimeout(if (readTimeout.isFinite) readTimeout.toMillis else 0, TimeUnit.MILLISECONDS)
        .build()
  }

  private[okhttp] def exceptionToSttpClientException(isWebsocket: Boolean, e: Exception): Option[Exception] =
    e match {
      // if the websocket protocol upgrade fails, OkHttp throws a ProtocolException - however the whole request has
      // been already sent, so this is not a TCP-level connect exception
      case e: java.net.ProtocolException if isWebsocket => Some(new ReadException(e))
      case e                                            => SttpClientException.defaultExceptionToSttpClientException(e)
    }
}

class OkHttpSyncBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int]
) extends OkHttpBackend[Identity, Nothing, WebSockets](client, closeClient, customEncodingHandler) {
  private implicit val ec: ExecutionContext = ExecutionContext.global
  override val streams: Streams[Nothing] = NoStreams

  override def send[T, R >: PE](request: Request[T, R]): Identity[Response[T]] = {
    if (request.isWebSocket) {
      sendWebSocket(request)
    } else {
      sendRegular(request)
    }
  }

  private def sendWebSocket[R >: PE, T](request: Request[T, R]) =
    adjustExceptions(isWebsocket = true) {
      val nativeRequest = convertRequest(request)
      val responseCell = new ArrayBlockingQueue[Either[Throwable, Future[Response[T]]]](5)
      def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))
      def fillCell(wr: Future[Response[T]]): Unit = responseCell.add(Right(wr))

      implicit val m = responseMonad
      val queue = createAsyncQueue[WebSocketEvent]
      val isOpen = new AtomicBoolean(false)
      val listener = new DelegatingWebSocketListener(
        new AddToQueueListener(queue, isOpen),
        { (nativeWs, response) =>
          val webSocket = new WebSocketImpl(nativeWs, queue, isOpen)
          val baseResponse = readResponse(response, ignore)
          val wsResponse =
            Future(
              blocking(
                bodyFromOkHttp(new ByteArrayInputStream(Array()), request.response, baseResponse, Some(webSocket))
              )
            )
              .map(b => baseResponse.copy(body = b))
          fillCell(wsResponse)
        },
        fillCellError
      )

      OkHttpBackend
        .updateClientIfCustomReadTimeout(request, client)
        .newWebSocket(nativeRequest, listener)

      val response = responseCell.take().fold(throw _, identity)
      Await.result(response, Duration.Inf)
    }

  private def sendRegular[R >: PE, T](request: Request[T, R]) = {
    adjustExceptions(isWebsocket = false) {
      val nativeRequest = convertRequest(request)
      val response = OkHttpBackend
        .updateClientIfCustomReadTimeout(request, client)
        .newCall(nativeRequest)
        .execute()
      readResponse(response, request.response)
    }
  }

  private def adjustExceptions[T](isWebsocket: Boolean)(t: => T): T =
    SttpClientException.adjustSynchronousExceptions(t)(OkHttpBackend.exceptionToSttpClientException(isWebsocket, _))

  override def responseMonad: MonadError[Identity] = IdMonad

  override protected val bodyFromOkHttp: BodyFromOkHttp[Identity, Nothing] = new BodyFromOkHttp[Identity, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Identity] = OkHttpSyncBackend.this.responseMonad
    override def responseBodyToStream(inputStream: InputStream): Nothing =
      throw new IllegalStateException("Streaming isn't supported")
    override def compileWebSocketPipe(ws: WebSocket[Identity], pipe: Nothing): Identity[Unit] = pipe
  }

  override protected val bodyToOkHttp: BodyToOkHttp[Identity, Nothing] = new BodyToOkHttp[Identity, Nothing] {
    override val streams: NoStreams = NoStreams
    override def streamToRequestBody(stream: Nothing): OkHttpRequestBody = stream
  }

  override protected def createAsyncQueue[T]: Identity[AsyncQueue[Identity, T]] =
    new SyncQueue[T](webSocketBufferCapacity)
}

object OkHttpSyncBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int]
  ): SttpBackend[Identity, WebSockets] =
    new FollowRedirectsBackend(
      new OkHttpSyncBackend(client, closeClient, customEncodingHandler, webSocketBufferCapacity)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  ): SttpBackend[Identity, WebSockets] =
    OkHttpSyncBackend(
      OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
      closeClient = true,
      customEncodingHandler,
      webSocketBufferCapacity
    )

  def usingClient(
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  ): SttpBackend[Identity, WebSockets] =
    OkHttpSyncBackend(client, closeClient = false, customEncodingHandler, webSocketBufferCapacity)

  /**
    * Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, Any] = SttpBackendStub.synchronous //TODO websockets?
}

abstract class OkHttpAsyncBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    monad: MonadAsyncError[F],
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends OkHttpBackend[F, S, P](client, closeClient, customEncodingHandler) {

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    if (request.isWebSocket) {
      sendWebSocket(request)
    } else {
      sendRegular(request)
    }
  }

  private def sendRegular[R >: PE, T](request: Request[T, R]) =
    adjustExceptions(isWebsocket = false) {
      val nativeRequest = convertRequest(request)
      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))

        def error(t: Throwable): Unit = cb(Left(t))

        val call = OkHttpBackend
          .updateClientIfCustomReadTimeout(request, client)
          .newCall(nativeRequest)

        call.enqueue(new Callback {
          override def onFailure(call: Call, e: IOException): Unit =
            error(e)

          override def onResponse(call: Call, response: OkHttpResponse): Unit =
            try success(readResponse(response, request.response))
            catch {
              case e: Exception =>
                response.close()
                error(e)
            }
        })

        Canceler(() => call.cancel())
      })
    }

  def sendWebSocket[T, R >: PE](
      request: Request[T, R]
  ): F[Response[T]] =
    adjustExceptions(isWebsocket = true) {
      implicit val m = monad
      val nativeRequest = convertRequest(request)
      monad.flatten(
        createAsyncQueue[WebSocketEvent]
          .flatMap { queue =>
            monad.async[F[Response[T]]] { cb =>
              val listener = createListener(queue, cb, request)
              val ws = OkHttpBackend
                .updateClientIfCustomReadTimeout(request, client)
                .newWebSocket(nativeRequest, listener)

              Canceler(() => ws.cancel())
            }
          }
      )
    }

  private def createListener[R >: PE, T](
      queue: AsyncQueue[F, WebSocketEvent],
      cb: Either[Throwable, F[Response[T]]] => Unit,
      request: Request[T, R]
  )(implicit m: MonadAsyncError[F]): DelegatingWebSocketListener = {
    val isOpen = new AtomicBoolean(false)
    val addToQueue = new AddToQueueListener(queue, isOpen)

    def onOpen(nativeWs: OkHttpWebSocket, response: OkHttpResponse) = {
      val webSocket = new WebSocketImpl(nativeWs, queue, isOpen)
      val wsResponse = readResponse(response, ignore)
        .flatMap { baseResponse =>
          bodyFromOkHttp(
            new ByteArrayInputStream(Array()), //TODO ugly hack
            request.response,
            baseResponse,
            Some(webSocket)
          ).map(b => baseResponse.copy(body = b))
        }
      cb(Right(wsResponse))
    }
    def onError(e: Throwable) = cb(Left(e))

    new DelegatingWebSocketListener(
      addToQueue,
      onOpen,
      onError
    )
  }

  private def adjustExceptions[T](isWebsocket: Boolean)(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(OkHttpBackend.exceptionToSttpClientException(isWebsocket, _))

  override def responseMonad: MonadError[F] = monad
}

class OkHttpFutureBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int]
)(implicit
    ec: ExecutionContext
) extends OkHttpAsyncBackend[Future, Nothing, WebSockets](client, new FutureMonad, closeClient, customEncodingHandler) {
  override val streams: Streams[Nothing] = NoStreams

  override protected def createAsyncQueue[T]: Future[AsyncQueue[Future, T]] =
    Future(new FutureAsyncQueue[T](webSocketBufferCapacity))

  override protected val bodyFromOkHttp: BodyFromOkHttp[Future, Nothing] = new BodyFromOkHttp[Future, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Future] = OkHttpFutureBackend.this.responseMonad
    override def responseBodyToStream(inputStream: InputStream): Nothing =
      throw new IllegalStateException("Streaming is not supported")
    override def compileWebSocketPipe(ws: WebSocket[Future], pipe: Nothing): Future[Unit] = pipe
  }

  override protected val bodyToOkHttp: BodyToOkHttp[Future, Nothing] = new BodyToOkHttp[Future, Nothing] {
    override val streams: NoStreams = NoStreams
    override def streamToRequestBody(stream: Nothing): OkHttpRequestBody = stream
  }
}

object OkHttpFutureBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int]
  )(implicit
      ec: ExecutionContext
  ): SttpBackend[Future, WebSockets] =
    new FollowRedirectsBackend(
      new OkHttpFutureBackend(client, closeClient, customEncodingHandler, webSocketBufferCapacity)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    OkHttpFutureBackend(
      OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
      closeClient = true,
      customEncodingHandler,
      webSocketBufferCapacity
    )

  def usingClient(
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    OkHttpFutureBackend(client, closeClient = false, customEncodingHandler, webSocketBufferCapacity)

  /**
    * Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackendStub[Future, WebSockets] =
    SttpBackendStub(new FutureMonad())
}

private[okhttp] class DelegatingWebSocketListener(
    delegate: WebSocketListener,
    onInitialOpen: (OkHttpWebSocket, OkHttpResponse) => Unit,
    onInitialError: Throwable => Unit
) extends WebSocketListener {
  private val initialised = new AtomicBoolean(false)

  override def onOpen(webSocket: OkHttpWebSocket, response: OkHttpResponse): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialOpen(webSocket, response)
    }
    delegate.onOpen(webSocket, response)
  }

  override def onFailure(webSocket: OkHttpWebSocket, t: Throwable, response: OkHttpResponse): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialError(t)
    }
    delegate.onFailure(webSocket, t, response)
  }

  override def onClosed(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit =
    delegate.onClosed(webSocket, code, reason)
  override def onClosing(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit =
    delegate.onClosing(webSocket, code, reason)
  override def onMessage(webSocket: OkHttpWebSocket, text: String): Unit = delegate.onMessage(webSocket, text)
  override def onMessage(webSocket: OkHttpWebSocket, bytes: ByteString): Unit = delegate.onMessage(webSocket, bytes)
}

class AddToQueueListener[F[_]](queue: AsyncQueue[F, WebSocketEvent], isOpen: AtomicBoolean) extends WebSocketListener {
  override def onOpen(websocket: OkHttpWebSocket, response: OkHttpResponse): Unit = {
    isOpen.set(true)
    queue.offer(WebSocketEvent.Open())
  }

  override def onClosed(webSocket: OkHttpWebSocket, code: Int, reason: String): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Frame(WebSocketFrame.Close(code, reason)))
  }

  override def onFailure(webSocket: OkHttpWebSocket, t: Throwable, response: OkHttpResponse): Unit = {
    isOpen.set(false)
    queue.offer(WebSocketEvent.Error(t))
  }

  override def onMessage(webSocket: OkHttpWebSocket, bytes: ByteString): Unit =
    onFrame(WebSocketFrame.Binary(bytes.toByteArray, finalFragment = true, None))
  override def onMessage(webSocket: OkHttpWebSocket, text: String): Unit = {
    onFrame(WebSocketFrame.Text(text, finalFragment = true, None))
  }

  private def onFrame(f: WebSocketFrame.Incoming): Unit = queue.offer(WebSocketEvent.Frame(f))
}
