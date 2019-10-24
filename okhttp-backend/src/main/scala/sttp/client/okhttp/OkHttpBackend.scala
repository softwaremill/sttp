package sttp.client.okhttp

import java.io.IOException
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import com.github.ghik.silencer.silent
import okhttp3.internal.http.HttpMethod
import okhttp3.{
  Authenticator,
  Call,
  Callback,
  Credentials,
  MediaType,
  OkHttpClient,
  Route,
  WebSocket,
  WebSocketListener,
  Headers => OkHttpHeaders,
  MultipartBody => OkHttpMultipartBody,
  Request => OkHttpRequest,
  RequestBody => OkHttpRequestBody,
  Response => OkHttpResponse
}
import okio.{BufferedSink, ByteString, Okio}
import sttp.client.ResponseAs.EagerResponseHandler
import sttp.client.SttpBackendOptions.Proxy
import sttp.client.internal.FileHelpers
import sttp.model._
import sttp.client.monad.{FutureMonad, IdMonad, MonadAsyncError, MonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{
  BasicResponseAs,
  IgnoreResponse,
  NoBody,
  RequestBody,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  SttpBackend,
  SttpBackendOptions,
  _
}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Try}

abstract class OkHttpBackend[F[_], S](client: OkHttpClient, closeClient: Boolean)
    extends SttpBackend[F, S, WebSocketHandler] {

  private[okhttp] def convertRequest[T](request: Request[T, S]): OkHttpRequest = {
    val builder = new OkHttpRequest.Builder()
      .url(request.uri.toString)

    val body = bodyToOkHttp(request.body, request.headers.find(_.is(HeaderNames.ContentType)).map(_.value))
    builder.method(request.method.method, body.getOrElse {
      if (HttpMethod.requiresRequestBody(request.method.method))
        OkHttpRequestBody.create("", null)
      else null
    })

    //OkHttp support automatic gzip compression
    request.headers
      .filterNot(h => h.is(HeaderNames.AcceptEncoding) || h.is(HeaderNames.ContentType))
      .foreach {
        case Header(name, value) => builder.addHeader(name, value)
      }

    builder.build()
  }

  private def bodyToOkHttp[T](body: RequestBody[S], ct: Option[String]): Option[OkHttpRequestBody] = {
    val mediaType = ct.flatMap(c => Try(MediaType.parse(c)).toOption).orNull
    body match {
      case NoBody => None
      case StringBody(b, _, _) =>
        Some(OkHttpRequestBody.create(b, mediaType))
      case ByteArrayBody(b, _) =>
        Some(OkHttpRequestBody.create(b, mediaType))
      case ByteBufferBody(b, _) =>
        Some(OkHttpRequestBody.create(b.array(), mediaType))
      case InputStreamBody(b, _) =>
        Some(new OkHttpRequestBody() {
          @silent("discarded")
          override def writeTo(sink: BufferedSink): Unit =
            sink.writeAll(Okio.source(b))
          override def contentType(): MediaType = mediaType
        })
      case FileBody(b, _) =>
        Some(OkHttpRequestBody.create(b.toFile, mediaType))
      case StreamBody(s) =>
        streamToRequestBody(s)
      case MultipartBody(ps) =>
        val b = new OkHttpMultipartBody.Builder()
          .setType(OkHttpMultipartBody.FORM)
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

  private[okhttp] def readResponse[T](
      res: OkHttpResponse,
      responseAs: ResponseAs[T, S]
  ): F[Response[T]] = {

    val headers = res
      .headers()
      .names()
      .asScala
      .flatMap(name => res.headers().values(name).asScala.map(Header(name, _)))
      .toList

    val responseMetadata = ResponseMetadata(headers, StatusCode.notValidated(res.code()), res.message())
    val body = responseHandler(res).handle(responseAs, responseMonad, responseMetadata)

    responseMonad.map(body)(Response(_, StatusCode.notValidated(res.code()), res.message(), headers, Nil))
  }

  private def responseHandler(res: OkHttpResponse) =
    new EagerResponseHandler[S] {
      override def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T] =
        bra match {
          case IgnoreResponse =>
            Try(res.close())
          case ResponseAsByteArray =>
            val body = Try(res.body().bytes())
            res.close()
            body
          case ras @ ResponseAsStream() =>
            responseBodyToStream(res).map(ras.responseIsStream)
          case ResponseAsFile(file) =>
            val body = Try(FileHelpers.saveFile(file.toFile, res.body().byteStream()))
            res.close()
            body.map(_ => file)
        }
    }

  def streamToRequestBody(stream: S): Option[OkHttpRequestBody] = None

  def responseBodyToStream(res: OkHttpResponse): Try[S] =
    Failure(new IllegalStateException("Streaming isn't supported"))

  override def close(): F[Unit] =
    if (closeClient) {
      responseMonad.eval(client.dispatcher().executorService().shutdown())
    } else responseMonad.unit(())
}

object OkHttpBackend {

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
}

class OkHttpSyncBackend private (client: OkHttpClient, closeClient: Boolean)
    extends OkHttpBackend[Identity, Nothing](client, closeClient) {
  override def send[T](r: Request[T, Nothing]): Response[T] = {
    val request = convertRequest(r)
    val response = OkHttpBackend
      .updateClientIfCustomReadTimeout(r, client)
      .newCall(request)
      .execute()
    readResponse(response, r.response)
  }

  override def openWebsocket[T, WS_RESULT](
      r: Request[T, Nothing],
      handler: WebSocketHandler[WS_RESULT]
  ): WebSocketResponse[WS_RESULT] = {

    val request = convertRequest(r)

    val responseCell = new ArrayBlockingQueue[Either[Throwable, WebSocketResponse[WS_RESULT]]](1)
    @silent("discarded")
    def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))
    @silent("discarded")
    def fillCell(wr: WebSocketResponse[WS_RESULT]): Unit = responseCell.add(Right(wr))

    val listener = new DelegatingWebSocketListener(
      handler.listener,
      (webSocket, response) => {
        val wsResponse =
          sttp.client.ws.WebSocketResponse(readResponse(response, ignore), handler.wrIsWebSocket(webSocket))
        fillCell(wsResponse)
      },
      fillCellError,
      handler.wrIsWebSocket
    )

    OkHttpBackend
      .updateClientIfCustomReadTimeout(r, client)
      .newWebSocket(request, listener)

    responseCell.take().fold(throw _, identity)
  }

  override def responseMonad: MonadError[Identity] = IdMonad
}

object OkHttpSyncBackend {
  private def apply(client: OkHttpClient, closeClient: Boolean): SttpBackend[Identity, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Identity, Nothing, WebSocketHandler](new OkHttpSyncBackend(client, closeClient))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Identity, Nothing, WebSocketHandler] =
    OkHttpSyncBackend(OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options), closeClient = true)

  def usingClient(client: OkHttpClient): SttpBackend[Identity, Nothing, WebSocketHandler] =
    OkHttpSyncBackend(client, closeClient = false)
}

abstract class OkHttpAsyncBackend[F[_], S](client: OkHttpClient, monad: MonadAsyncError[F], closeClient: Boolean)
    extends OkHttpBackend[F, S](client, closeClient) {
  override def send[T](r: Request[T, S]): F[Response[T]] = {
    val request = convertRequest(r)

    monad.flatten(monad.async[F[Response[T]]] { cb =>
      def success(r: F[Response[T]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      OkHttpBackend
        .updateClientIfCustomReadTimeout(r, client)
        .newCall(request)
        .enqueue(new Callback {
          override def onFailure(call: Call, e: IOException): Unit =
            error(e)

          override def onResponse(call: Call, response: OkHttpResponse): Unit =
            try success(readResponse(response, r.response))
            catch { case e: Exception => error(e) }
        })
    })
  }

  override def openWebsocket[T, WS_RESULT](
      r: Request[T, S],
      handler: WebSocketHandler[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = {

    val request = convertRequest(r)

    monad.flatten(monad.async[F[WebSocketResponse[WS_RESULT]]] { cb =>
      def success(r: F[WebSocketResponse[WS_RESULT]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      val listener = new DelegatingWebSocketListener(
        handler.listener,
        (webSocket, response) => {
          val wsResponse =
            monad.map(readResponse(response, ignore))(
              r => sttp.client.ws.WebSocketResponse(r, handler.wrIsWebSocket(webSocket))
            )
          success(wsResponse)
        },
        error,
        handler.wrIsWebSocket
      )

      val _ = OkHttpBackend
        .updateClientIfCustomReadTimeout(r, client)
        .newWebSocket(request, listener)
    })
  }

  override def responseMonad: MonadError[F] = monad
}

class OkHttpFutureBackend private (client: OkHttpClient, closeClient: Boolean)(implicit ec: ExecutionContext)
    extends OkHttpAsyncBackend[Future, Nothing](client, new FutureMonad, closeClient) {}

object OkHttpFutureBackend {
  private def apply(client: OkHttpClient, closeClient: Boolean)(
      implicit ec: ExecutionContext
  ): SttpBackend[Future, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Future, Nothing, WebSocketHandler](new OkHttpFutureBackend(client, closeClient))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing, WebSocketHandler] =
    OkHttpFutureBackend(OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options), closeClient = true)

  def usingClient(
      client: OkHttpClient
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing, WebSocketHandler] =
    OkHttpFutureBackend(client, closeClient = false)
}

private[okhttp] class DelegatingWebSocketListener[WS_RESULT](
    delegate: WebSocketListener,
    onInitialOpen: (WebSocket, OkHttpResponse) => Unit,
    onInitialError: Throwable => Unit,
    wrIsWebSocket: WebSocket =:= WS_RESULT
) extends WebSocketListener {
  private val initialised = new AtomicBoolean(false)

  override def onOpen(webSocket: WebSocket, response: OkHttpResponse): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialOpen(webSocket, response)
    }
    delegate.onOpen(webSocket, response)
  }

  override def onFailure(webSocket: WebSocket, t: Throwable, response: OkHttpResponse): Unit = {
    if (!initialised.getAndSet(true)) {
      onInitialError(t)
    }
    delegate.onFailure(webSocket, t, response)
  }

  override def onClosed(webSocket: WebSocket, code: Int, reason: String): Unit =
    delegate.onClosed(webSocket, code, reason)
  override def onClosing(webSocket: WebSocket, code: Int, reason: String): Unit =
    delegate.onClosing(webSocket, code, reason)
  override def onMessage(webSocket: WebSocket, text: String): Unit = delegate.onMessage(webSocket, text)
  override def onMessage(webSocket: WebSocket, bytes: ByteString): Unit = delegate.onMessage(webSocket, bytes)
}
