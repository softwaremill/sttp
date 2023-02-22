package sttp.client3.okhttp

import java.io.InputStream
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3.internal.NoStreams
import sttp.client3.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client3.okhttp.OkHttpBackend.EncodingHandler
import sttp.client3.testing.WebSocketBackendStub
import sttp.client3.{DefaultReadTimeout, FollowRedirectsBackend, BackendOptions, WebSocketBackend}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.WebSocket

import scala.concurrent.{ExecutionContext, Future}

class OkHttpFutureBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int]
)(implicit
    ec: ExecutionContext
) extends OkHttpAsyncBackend[Future, Nothing, WebSockets](client, new FutureMonad, closeClient, customEncodingHandler)
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
}

object OkHttpFutureBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int]
  )(implicit
      ec: ExecutionContext
  ): WebSocketBackend[Future] =
    FollowRedirectsBackend(new OkHttpFutureBackend(client, closeClient, customEncodingHandler, webSocketBufferCapacity))

  def apply(
             options: BackendOptions = BackendOptions.Default,
             customEncodingHandler: EncodingHandler = PartialFunction.empty,
             webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
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
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
    OkHttpFutureBackend(client, closeClient = false, customEncodingHandler, webSocketBufferCapacity)

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackendStub[Future] =
    WebSocketBackendStub.asynchronousFuture
}
