package sttp.client.okhttp

import java.io.{IOException, InputStream, UnsupportedEncodingException}
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import okhttp3.internal.http.HttpMethod
import okhttp3.{Authenticator, Call, Callback, Credentials, MediaType, OkHttpClient, Route, WebSocket=>OkHttpWebSocket, WebSocketListener, Headers => OkHttpHeaders, MultipartBody => OkHttpMultipartBody, Request => OkHttpRequest, RequestBody => OkHttpRequestBody, Response => OkHttpResponse}
import okio.{BufferedSink, ByteString, Okio}
import sttp.client.ResponseAs.EagerResponseHandler
import sttp.client.SttpBackendOptions.Proxy
import sttp.client.SttpClientException.ReadException
import sttp.client.internal.{FileHelpers, NoStreams, toByteArray}
import sttp.model._
import sttp.client.monad.{Canceler, FutureMonad, IdMonad, MonadAsyncError, MonadError}
import sttp.client.okhttp.OkHttpBackend.EncodingHandler
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.{AsyncQueue, WebSocketEvent}
import sttp.client.{BasicResponseAs, IgnoreResponse, NoBody, RequestBody, Response, ResponseAs, ResponseAsByteArray, SttpBackend, SttpBackendOptions, _}
import sttp.model.ws.WebSocketFrame
import sttp.client.monad.syntax._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

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

  private def bodyToOkHttp[T, R >: PE](body: RequestBody[R], ct: Option[String]): Option[OkHttpRequestBody] = {
    val mediaType = ct.flatMap(c => Try(MediaType.parse(c)).toOption).orNull
    body match {
      case NoBody => None
      case StringBody(b, _, _) =>
        Some(OkHttpRequestBody.create(b, mediaType))
      case ByteArrayBody(b, _) =>
        Some(OkHttpRequestBody.create(b, mediaType))
      case ByteBufferBody(b, _) =>
        if (b.isReadOnly) Some(OkHttpRequestBody.create(ByteString.of(b), mediaType))
        else Some(OkHttpRequestBody.create(b.array(), mediaType))
      case InputStreamBody(b, _) =>
        Some(new OkHttpRequestBody() {
          override def writeTo(sink: BufferedSink): Unit =
            sink.writeAll(Okio.source(b))
          override def contentType(): MediaType = mediaType
        })
      case FileBody(b, _) =>
        Some(OkHttpRequestBody.create(b.toFile, mediaType))
      case StreamBody(s) =>
        streamToRequestBody(s.asInstanceOf[streams.BinaryStream])
      case MultipartBody(ps) =>
        val b = new OkHttpMultipartBody.Builder().setType(Option(mediaType).getOrElse(OkHttpMultipartBody.FORM))
        ps.foreach(addMultipart(b, _))
        Some(b.build())
    }
  }

  private def addMultipart(builder: OkHttpMultipartBody.Builder, mp: Part[BasicRequestBody]): Unit = {
    val allHeaders = mp.headers :+ Header(HeaderNames.ContentDisposition, mp.contentDispositionHeaderValue)
    val headers =
      OkHttpHeaders.of(allHeaders.filterNot(_.is(HeaderNames.ContentType)).map(h => (h.name, h.value)).toMap.asJava)

    bodyToOkHttp(mp.body, mp.contentType).foreach(builder.addPart(headers, _))
  }

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

    val body = responseHandler(byteBody).handle(responseAs, responseMonad, responseMetadata)
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

  private def responseHandler[R >: PE](responseBody: InputStream): EagerResponseHandler[R, F] =
    new EagerResponseHandler[R, F] {
      override def handleStream[T](ras: ResponseAsStream[F, _, _, _]): F[T] =
        responseMonad.ensure(
          responseMonad.flatMap(responseMonad.fromTry(responseBodyToStream(responseBody)))(
            ras.f.asInstanceOf[streams.BinaryStream => F[T]]
          ),
          responseMonad.eval(responseBody.close())
        )

      override def handleBasic[T](bra: BasicResponseAs[T, R]): Try[T] =
        bra match {
          case IgnoreResponse =>
            Try(responseBody.close())
          case ResponseAsByteArray =>
            val body = Try(toByteArray(responseBody))
            responseBody.close()
            body
          case _: ResponseAsStreamUnsafe[_, _] =>
            responseBodyToStream(responseBody).asInstanceOf[Try[T]]
          case ResponseAsFile(file) =>
            val body = Try(FileHelpers.saveFile(file.toFile, responseBody))
            responseBody.close()
            body.map(_ => file)
        }
    }

  def streamToRequestBody(stream: streams.BinaryStream): Option[OkHttpRequestBody] = None

  def responseBodyToStream(inputStream: InputStream): Try[streams.BinaryStream] =
    Failure(new IllegalStateException("Streaming isn't supported"))

  override def close(): F[Unit] =
    if (closeClient) {
      responseMonad.eval(client.dispatcher().executorService().shutdown())
    } else responseMonad.unit(())


  protected def createAsyncQueue[T]: F[AsyncQueue[F, T]]

  protected def bodyFromWs[TT](r: WebSocketResponseAs[TT, _], ws: WebSocket[F]): F[TT] =
    r match {
      case ResponseAsWebSocket(f)      => f.asInstanceOf[WebSocket[F] => F[TT]](ws).ensure(ws.close)
      case ResponseAsWebSocketUnsafe() => ws.unit.asInstanceOf[F[TT]]
      case ResponseAsWebSocketStream(_, p) =>
        compileWebSocketPipe(ws, p.asInstanceOf[streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]])
    }

  protected def compileWebSocketPipe(ws: WebSocket[F], pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): F[Unit]
}

object OkHttpBackend {

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

class OkHttpSyncBackend private (client: OkHttpClient, closeClient: Boolean, customEncodingHandler: EncodingHandler)
    extends OkHttpBackend[Identity, Nothing, WebSockets](client, closeClient, customEncodingHandler) {

  override val streams: Streams[Nothing] = NoStreams

  override def send[T, R >: PE](request: Request[T, R]): Identity[Response[T]] = {
    if (request.isWebSocket) {
      sendWebSocket(request)
    }else{
      sendRegular(request)
    }
  }

  private def sendWebSocket[R >: PE, T](request: Request[T, R]) = adjustExceptions(isWebsocket = true) {
    val nativeRequest = convertRequest(request)
    val responseCell = new ArrayBlockingQueue[Either[Throwable, Response[T]]](1)
    def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))
    def fillCell(wr: Response[T]): Unit = responseCell.add(Right(wr))
    
    val listener = new DelegatingWebSocketListener(???,{ (nativeWs,response)=> //TODO
      val queue = createAsyncQueue[WebSocketEvent] // TODO should it be the arrayBlockingQueue?
      val isOpen = new AtomicBoolean(false)
      val webSocket = new WebSocketImpl(nativeWs, queue, isOpen)
      val baseResponse = readResponse(response, ignore)
      val wsResponse = bodyFromWs(request.response.asInstanceOf[WebSocketResponseAs[T, R]], webSocket).map(b => baseResponse.copy(body = b))
      fillCell(wsResponse)
    },fillCellError)

    OkHttpBackend
      .updateClientIfCustomReadTimeout(request, client)
      .newWebSocket(nativeRequest, listener)
    
    responseCell.take().fold(throw _, identity)
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

  override protected def compileWebSocketPipe(ws: WebSocket[Identity], pipe: Nothing): Identity[Unit] = pipe

  override protected def createAsyncQueue[T]: Identity[AsyncQueue[Identity, T]] = ??? //TODO
}

object OkHttpSyncBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler
  ): SttpBackend[Identity, WebSockets] =
    new FollowRedirectsBackend(
      new OkHttpSyncBackend(client, closeClient, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Identity, WebSockets] =
    OkHttpSyncBackend(
      OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
      closeClient = true,
      customEncodingHandler
    )

  def usingClient(
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Identity, WebSockets] =
    OkHttpSyncBackend(client, closeClient = false, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, Any] = SttpBackendStub.synchronous//TODO websockets?
}

abstract class OkHttpAsyncBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    monad: MonadAsyncError[F],
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends OkHttpBackend[F, S, P](client, closeClient, customEncodingHandler) {

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
      if(request.isWebSocket) {
        sendWebSocket(request)
      }else{
        sendRegular(request)
    }
  }

  private def sendRegular[R >: PE, T](request: Request[T, R]) = adjustExceptions(isWebsocket = false) {
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
  ): F[Response[T]] = adjustExceptions(isWebsocket = true) {
      implicit val m = monad
      val nativeRequest = convertRequest(request)
      monad.flatten(
        createAsyncQueue[WebSocketEvent]
          .flatMap { queue =>
            monad.async[F[Response[T]]] { cb =>
              val isOpen = new AtomicBoolean(false)
              val listener = new DelegatingWebSocketListener(new AddToQueueListener(queue,isOpen), { (nativeWs, response) =>
                val webSocket = new WebSocketImpl(nativeWs, queue, isOpen)
                val wsResponse = readResponse(response, ignore).flatMap { baseResponse =>
                  bodyFromWs(request.response.asInstanceOf[WebSocketResponseAs[T, R]], webSocket).map(b => baseResponse.copy(body = b))
                }
                cb(Right(wsResponse))
              }, e => cb(Left(e)))
      
              val ws = OkHttpBackend
                .updateClientIfCustomReadTimeout(request, client)
                .newWebSocket(nativeRequest, listener)
      
              Canceler(() => ws.cancel())
            }
      })
    }

  private def adjustExceptions[T](isWebsocket: Boolean)(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(OkHttpBackend.exceptionToSttpClientException(isWebsocket, _))

  override def responseMonad: MonadError[F] = monad
}

class OkHttpFutureBackend private (client: OkHttpClient, closeClient: Boolean, customEncodingHandler: EncodingHandler)(
    implicit ec: ExecutionContext
) extends OkHttpAsyncBackend[Future, Nothing, Any](client, new FutureMonad, closeClient, customEncodingHandler) {
  override val streams: Streams[Nothing] = NoStreams

  override protected def createAsyncQueue[T]: Future[AsyncQueue[Future, T]] = throw new IllegalStateException("Web sockets are not supported!") //TODO why?

  override protected def compileWebSocketPipe(ws: WebSocket[Future], pipe: Nothing): Future[Unit] = pipe
}

object OkHttpFutureBackend {
  private def apply(client: OkHttpClient, closeClient: Boolean, customEncodingHandler: EncodingHandler)(implicit
      ec: ExecutionContext
  ): SttpBackend[Future, Any] =
    new FollowRedirectsBackend(
      new OkHttpFutureBackend(client, closeClient, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, Any] =
    OkHttpFutureBackend(
      OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
      closeClient = true,
      customEncodingHandler
    )

  def usingClient(
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, Any] =
    OkHttpFutureBackend(client, closeClient = false, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackendStub[Future, Any] =
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

class AddToQueueListener[F[_]](queue: AsyncQueue[F, WebSocketEvent], isOpen: AtomicBoolean)
  extends WebSocketListener {
  override def onOpen(websocket: OkHttpWebSocket,response: OkHttpResponse): Unit = {
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
