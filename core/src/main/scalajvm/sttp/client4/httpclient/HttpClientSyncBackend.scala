package sttp.client4.httpclient

import sttp.capabilities.WebSockets
import sttp.client4.internal.httpclient._
import sttp.client4.internal.ws.{SimpleQueue, SyncQueue, WebSocketEvent}
import sttp.client4.internal.{emptyInputStream, NoStreams}
import sttp.client4.testing.WebSocketSyncBackendStub
import sttp.client4.{wrappers, BackendOptions, GenericRequest, Response, WebSocketSyncBackend}
import sttp.model.StatusCode
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, WebSocketHandshakeException}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ArrayBlockingQueue, CompletionException}
import sttp.client4.compression.Compressor
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Decompressor
import sttp.tapir.server.jdkhttp.internal.FailingLimitedInputStream

class HttpClientSyncBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compression: CompressionHandlers[Any, InputStream]
) extends HttpClientBackend[Identity, Nothing, WebSockets, InputStream](
      client,
      closeClient,
      compression
    )
    with WebSocketSyncBackend {

  override val streams: NoStreams = NoStreams

  override protected def sendRegular[T](request: GenericRequest[T, R]): Response[T] = {
    val jRequest = customizeRequest(convertRequest(request))
    val response = client.send(jRequest, BodyHandlers.ofInputStream())
    val body = response.body()
    val limitedBody = request.options.maxResponseBodyLength.fold(body)(new FailingLimitedInputStream(body, _))
    readResponse(response, Left(limitedBody), request)
  }

  override protected def sendWebSocket[T](request: GenericRequest[T, R]): Response[T] = {
    val queue = new SyncQueue[WebSocketEvent](None)
    val sequencer = new IdSequencer
    try sendWebSocket(request, queue, sequencer)
    catch {
      case e: CompletionException if e.getCause.isInstanceOf[WebSocketHandshakeException] =>
        readResponse(
          e.getCause.asInstanceOf[WebSocketHandshakeException].getResponse,
          Left(emptyInputStream()),
          request
        )
    }
  }

  private def sendWebSocket[T](
      request: GenericRequest[T, R],
      queue: SimpleQueue[Identity, WebSocketEvent],
      sequencer: Sequencer[Identity]
  ): Response[T] = {
    val isOpen: AtomicBoolean = new AtomicBoolean(false)
    val responseCell = new ArrayBlockingQueue[Either[Throwable, () => Response[T]]](1)

    def fillCellError(t: Throwable): Unit = { val _ = responseCell.add(Left(t)) }
    def fillCell(wr: () => Response[T]): Unit = { val _ = responseCell.add(Right(wr)) }

    val listener = new DelegatingWebSocketListener(
      new AddToQueueListener(queue, isOpen),
      ws => {
        val webSocket = new WebSocketImpl[Identity](ws, queue, isOpen, sequencer, monad, cf => { val _ = cf.get() })
        val baseResponse = Response((), StatusCode.SwitchingProtocols, "", Nil, Nil, request.onlyMetadata)
        val body = () => bodyFromHttpClient(Right(webSocket), request.response, baseResponse)
        fillCell(() => baseResponse.copy(body = body()))
      },
      fillCellError
    )
    prepareWebSocketBuilder(request, client)
      .buildAsync(request.uri.toJavaUri, listener)
      .get()
    responseCell.take().fold(throw _, f => f())
  }

  override protected val bodyToHttpClient = new BodyToHttpClient[Identity, Nothing, R] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Identity] = IdentityMonad
    override def streamToPublisher(stream: Nothing): Identity[BodyPublisher] = stream // nothing is everything
    override def compressors: List[Compressor[R]] = compression.compressors
  }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Identity, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[Identity, Nothing] {
      override def inputStreamToStream(is: InputStream): Identity[(streams.BinaryStream, () => Identity[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[Identity] = IdentityMonad
      override def compileWebSocketPipe(
          ws: WebSocket[Identity],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): Identity[Unit] = pipe
    }
}

object HttpClientSyncBackend {
  val DefaultCompressionHandlers: CompressionHandlers[Any, InputStream] =
    CompressionHandlers(Compressor.default[Any], Decompressor.defaultInputStream)

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[Any, InputStream]
  ): WebSocketSyncBackend =
    wrappers.FollowRedirectsBackend(
      new HttpClientSyncBackend(client, closeClient, customizeRequest, compressionHandlers)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  ): WebSocketSyncBackend =
    HttpClientSyncBackend(
      HttpClientBackend.defaultClient(options, None),
      closeClient = true,
      customizeRequest,
      compressionHandlers
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  ): WebSocketSyncBackend =
    HttpClientSyncBackend(
      client,
      closeClient = false,
      customizeRequest,
      compressionHandlers
    )

  /** Create a stub backend for testing. See [[WebSocketBackendStub]] for details on how to configure stub responses. */
  def stub: WebSocketSyncBackendStub = WebSocketSyncBackendStub
}
