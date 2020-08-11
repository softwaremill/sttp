package sttp.client.okhttp

import java.io.InputStream

import okhttp3.{OkHttpClient, RequestBody => OkHttpRequestBody}
import sttp.client.internal.NoStreams
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.okhttp.OkHttpBackend.EncodingHandler
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.{AsyncQueue, FutureAsyncQueue}
import sttp.client.{DefaultReadTimeout, FollowRedirectsBackend, Streams, SttpBackend, SttpBackendOptions, WebSockets}

import scala.concurrent.{ExecutionContext, Future}

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
