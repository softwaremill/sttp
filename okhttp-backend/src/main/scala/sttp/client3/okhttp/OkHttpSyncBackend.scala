package sttp.client3.okhttp

import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{OkHttpClient, RequestBody => OkHttpRequestBody}
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3.internal.NoStreams
import sttp.client3.internal.ws.{SimpleQueue, SyncQueue, WebSocketEvent}
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpBackend.EncodingHandler
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{
  DefaultReadTimeout,
  FollowRedirectsBackend,
  Identity,
  Request,
  Response,
  SttpBackend,
  SttpBackendOptions,
  ignore
}
import sttp.monad.MonadError
import sttp.ws.WebSocket
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

import sttp.client3.FollowRedirectsBackend.UriEncoder

class OkHttpSyncBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int]
) extends OkHttpBackend[Identity, Nothing, WebSockets](client, closeClient, customEncodingHandler) {
  private implicit val ec: ExecutionContext = ExecutionContext.global
  override val streams: Streams[Nothing] = NoStreams

  override protected def sendWebSocket[T, R >: PE](request: Request[T, R]): Identity[Response[T]] = {
    val nativeRequest = convertRequest(request)
    val responseCell = new ArrayBlockingQueue[Either[Throwable, Future[Response[T]]]](5)
    def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))
    def fillCell(wr: Future[Response[T]]): Unit = responseCell.add(Right(wr))

    implicit val m = responseMonad
    val queue = createSimpleQueue[WebSocketEvent]
    val isOpen = new AtomicBoolean(false)
    val listener = new DelegatingWebSocketListener(
      new AddToQueueListener(queue, isOpen),
      { (nativeWs, response) =>
        val webSocket = new WebSocketImpl(nativeWs, queue, isOpen, response.headers())
        val baseResponse = readResponse(response, request.response(ignore))
        val wsResponse =
          Future(
            blocking(
              bodyFromOkHttp(response.body().byteStream(), request.response, baseResponse, Some(webSocket))
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

  override protected def sendRegular[T, R >: PE](request: Request[T, R]): Identity[Response[T]] = {
    val nativeRequest = convertRequest(request)
    val response = OkHttpBackend
      .updateClientIfCustomReadTimeout(request, client)
      .newCall(nativeRequest)
      .execute()
    readResponse(response, request)
  }

  override val responseMonad: MonadError[Identity] = IdMonad

  override protected val bodyFromOkHttp: BodyFromOkHttp[Identity, Nothing] = new BodyFromOkHttp[Identity, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Identity] = IdMonad
    override def responseBodyToStream(inputStream: InputStream): Nothing =
      throw new IllegalStateException("Streaming isn't supported")
    override def compileWebSocketPipe(ws: WebSocket[Identity], pipe: Nothing): Identity[Unit] = pipe
  }

  override protected val bodyToOkHttp: BodyToOkHttp[Identity, Nothing] = new BodyToOkHttp[Identity, Nothing] {
    override val streams: NoStreams = NoStreams
    override def streamToRequestBody(stream: Nothing): OkHttpRequestBody = stream
  }

  override protected def createSimpleQueue[T]: Identity[SimpleQueue[Identity, T]] =
    new SyncQueue[T](webSocketBufferCapacity)
}

object OkHttpSyncBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int],
      uriEncoder: UriEncoder
  ): SttpBackend[Identity, WebSockets] =
    new FollowRedirectsBackend(
      new OkHttpSyncBackend(client, closeClient, customEncodingHandler, webSocketBufferCapacity),
      uriEncoder = uriEncoder
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): SttpBackend[Identity, WebSockets] =
    OkHttpSyncBackend(
      OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
      closeClient = true,
      customEncodingHandler,
      webSocketBufferCapacity,
      uriEncoder
    )

  def usingClient(
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): SttpBackend[Identity, WebSockets] =
    OkHttpSyncBackend(client, closeClient = false, customEncodingHandler, webSocketBufferCapacity, uriEncoder)

  /** Create a stub backend for testing, which uses the [[Identity]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Identity, WebSockets] = SttpBackendStub.synchronous
}
