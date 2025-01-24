package sttp.client4.okhttp

import java.io.InputStream
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import sttp.capabilities.{Streams, WebSockets}
import sttp.client4.internal.NoStreams
import sttp.client4.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{wrappers, BackendOptions, DefaultReadTimeout, WebSocketBackend}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.WebSocket

import scala.concurrent.{ExecutionContext, Future}
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.compression.Decompressor

class OkHttpFutureBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    compressionHandlers: CompressionHandlers[Any, InputStream],
    webSocketBufferCapacity: Option[Int]
)(implicit
    ec: ExecutionContext
) extends OkHttpAsyncBackend[Future, Nothing, WebSockets](client, new FutureMonad, closeClient, compressionHandlers)
    with WebSocketBackend[Future] {
  override val streams: Streams[Nothing] = NoStreams

  override protected def createSimpleQueue[T]: Future[SimpleQueue[Future, T]] =
    Future(new FutureSimpleQueue[T](webSocketBufferCapacity))

  override protected val bodyFromOkHttp: BodyFromOkHttp[Future, Nothing] = new BodyFromOkHttp[Future, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadError[Future] = new FutureMonad
    override def responseBodyToStream(inputStream: InputStream): Nothing =
      throw new IllegalStateException("Streaming is not supported")
    override def compileWebSocketPipe(ws: WebSocket[Future], pipe: Nothing): Future[Unit] = pipe
  }

  override protected val bodyToOkHttp: BodyToOkHttp[Future, Nothing] = new BodyToOkHttp[Future, Nothing] {
    override val streams: NoStreams = NoStreams
    override def streamToRequestBody(stream: Nothing, mt: MediaType, cl: Option[Long]): OkHttpRequestBody = stream
  }

  override protected def ensureOnAbnormal[T](effect: Future[T])(finalizer: => Future[Unit]): Future[T] =
    effect.recoverWith { case e =>
      finalizer.recoverWith { case e2 => e.addSuppressed(e2); Future.failed(e) }.flatMap(_ => Future.failed(e))
    }
}

object OkHttpFutureBackend {
  val DefaultCompressionHandlers: CompressionHandlers[Any, InputStream] =
    CompressionHandlers(Compressor.default[Any], Decompressor.defaultInputStream)

  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      compressionHandlers: CompressionHandlers[Any, InputStream],
      webSocketBufferCapacity: Option[Int]
  )(implicit
      ec: ExecutionContext
  ): WebSocketBackend[Future] =
    wrappers.FollowRedirectsBackend(
      new OkHttpFutureBackend(client, closeClient, compressionHandlers, webSocketBufferCapacity)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
    OkHttpFutureBackend(
      OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
      closeClient = true,
      compressionHandlers,
      webSocketBufferCapacity
    )

  def usingClient(
      client: OkHttpClient,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
    OkHttpFutureBackend(client, closeClient = false, compressionHandlers, webSocketBufferCapacity)

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackendStub[Future] =
    WebSocketBackendStub.asynchronousFuture
}
