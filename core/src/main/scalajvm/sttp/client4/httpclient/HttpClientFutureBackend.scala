package sttp.client4.httpclient

import sttp.client4.internal.httpclient._
import sttp.client4.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client4.internal.{emptyInputStream, NoStreams}
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.{wrappers, BackendOptions, WebSocketBackend}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.Executor
import scala.concurrent.{ExecutionContext, Future}
import sttp.client4.compression.GZipInputStreamDecompressor
import sttp.client4.compression.DeflateInputStreamDecompressor
import sttp.client4.compression.Compressor
import sttp.client4.compression.CompressionHandlers

class HttpClientFutureBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[Any, InputStream]
)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing, InputStream, InputStream](
      client,
      new FutureMonad,
      closeClient,
      customizeRequest,
      compressionHandlers
    ) {

  override val streams: NoStreams = NoStreams

  override protected val bodyToHttpClient = new BodyToHttpClient[Future, Nothing, R] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Future] = new FutureMonad
    override def streamToPublisher(stream: Nothing): Future[BodyPublisher] = stream // nothing is everything
    override def compressors: List[Compressor[Nothing]] = compressionHandlers.compressors
  }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Future, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[Future, Nothing] {
      override def inputStreamToStream(is: InputStream): Future[(streams.BinaryStream, () => Future[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[Future] = new FutureMonad()
      override def compileWebSocketPipe(
          ws: WebSocket[Future],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): Future[Unit] = pipe
    }

  override protected def createSimpleQueue[T]: Future[SimpleQueue[Future, T]] =
    Future.successful(new FutureSimpleQueue[T](None))

  override protected def createSequencer: Future[Sequencer[Future]] = Future.successful(new FutureSequencer)

  override protected def createBodyHandler: HttpResponse.BodyHandler[InputStream] = BodyHandlers.ofInputStream()

  override protected def bodyHandlerBodyToBody(p: InputStream): InputStream = p

  override protected def emptyBody(): InputStream = emptyInputStream()
}

object HttpClientFutureBackend {
  val DefaultCompressionHandlers: CompressionHandlers[Any, InputStream] = CompressionHandlers(
    Compressor.default[Any],
    List(GZipInputStreamDecompressor, DeflateInputStreamDecompressor)
  )

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[Any, InputStream]
  )(implicit ec: ExecutionContext): WebSocketBackend[Future] =
    wrappers.FollowRedirectsBackend(
      new HttpClientFutureBackend(client, closeClient, customizeRequest, compressionHandlers)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] = {
    val executor = Some(ec).collect { case executor: Executor => executor }
    HttpClientFutureBackend(
      HttpClientBackend.defaultClient(options, executor),
      closeClient = executor.isEmpty,
      customizeRequest,
      compressionHandlers
    )
  }

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
    HttpClientFutureBackend(
      client,
      closeClient = false,
      customizeRequest,
      compressionHandlers
    )

  /** Create a stub backend for testing, which uses [[Future]] to represent side effects, and doesn't support streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackendStub[Future] =
    WebSocketBackendStub.asynchronousFuture
}
