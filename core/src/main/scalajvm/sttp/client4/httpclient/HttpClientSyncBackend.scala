package sttp.client4.httpclient

import sttp.capabilities.WebSockets
import sttp.client4.BackendOptions
import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.client4.WebSocketSyncBackend
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.compression.Decompressor
import sttp.client4.internal.FailingLimitedInputStream
import sttp.client4.internal.NoStreams
import sttp.client4.internal.OnEndInputStream
import sttp.client4.internal.emptyInputStream
import sttp.client4.internal.httpclient._
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.internal.ws.SyncQueue
import sttp.client4.internal.ws.WebSocketEvent
import sttp.client4.testing.WebSocketSyncBackendStub
import sttp.client4.wrappers
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.ws.WebSocket
import sttp.ws.WebSocketFrame

import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    try {
      val body = response.body()
      val limitedBody = request.options.maxResponseBodyLength.fold(body)(new FailingLimitedInputStream(body, _))
      readResponse(response, Left(limitedBody), request)
    } catch {
      case e: Throwable =>
        // ensuring that the response body always gets closed
        // in case of success the body is either already consumed, or an `...Unsafe` response description is used and
        // it's up to the user to consume it
        try {
          response.body().close()
        } catch {
          case e2: Throwable => e.addSuppressed(e2)
        }
        throw e
    }
  }

  override protected def sendWebSocket[T](request: GenericRequest[T, R]): Response[T] = {
    val queue = new SyncQueue[WebSocketEvent](None)
    val sequencer = new IdSequencer
    // see HttpClientAsyncBackend.sendRegular for explanation
    val lowLevelWS = new AtomicReference[java.net.http.WebSocket]()
    try sendWebSocket(request, queue, sequencer, lowLevelWS)
    catch {
      case e: CompletionException if e.getCause.isInstanceOf[WebSocketHandshakeException] =>
        readResponse(
          e.getCause.asInstanceOf[WebSocketHandshakeException].getResponse,
          Left(emptyInputStream()),
          request
        )
      case e: Throwable =>
        try {
          val llws = lowLevelWS.get()
          if (llws != null) llws.abort()
        } catch {
          case e2: Throwable => e.addSuppressed(e2)
        }
        throw e
    }
  }

  private def sendWebSocket[T](
      request: GenericRequest[T, R],
      queue: SimpleQueue[Identity, WebSocketEvent],
      sequencer: Sequencer[Identity],
      lowLevelWS: AtomicReference[java.net.http.WebSocket]
  ): Response[T] = {
    val isOpen: AtomicBoolean = new AtomicBoolean(false)
    val responseCell = new ArrayBlockingQueue[Either[Throwable, () => Response[T]]](1)

    def fillCellError(t: Throwable): Unit = { val _ = responseCell.add(Left(t)) }
    def fillCell(wr: () => Response[T]): Unit = { val _ = responseCell.add(Right(wr)) }

    val listener = new DelegatingWebSocketListener(
      new AddToQueueListener(queue, isOpen),
      ws => {
        lowLevelWS.set(ws)
        val webSocket = new WebSocketImpl[Identity](ws, queue, isOpen, sequencer, monad, cf => { val _ = cf.get() })

        val subprotocol = ws.getSubprotocol()
        val headers =
          if (subprotocol != null && subprotocol.nonEmpty) List(Header(HeaderNames.SecWebSocketProtocol, subprotocol))
          else Nil
        val baseResponse = Response((), StatusCode.SwitchingProtocols, "", headers, Nil, request.onlyMetadata)

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

  override protected val bodyToHttpClient: BodyToHttpClient[Identity, Nothing, R] =
    new BodyToHttpClient[Identity, Nothing, R] {
      override val streams: NoStreams = NoStreams
      override implicit val monad: MonadError[Identity] = IdentityMonad
      override val multiPartBodyBuilder: MultipartBodyBuilder[Nothing, Identity] =
        new NonStreamMultipartBodyBuilder[NoStreams.BinaryStream, Identity] {}
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

  override def addOnEndCallbackToBody(b: InputStream, callback: () => Unit): InputStream =
    new OnEndInputStream(b, callback)
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
