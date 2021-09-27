package sttp.client3.okhttp

import java.io.InputStream

import okhttp3.{OkHttpClient, RequestBody => OkHttpRequestBody}
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3.internal.NoStreams
import sttp.client3.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client3.okhttp.OkHttpBackend.EncodingHandler
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{DefaultReadTimeout, FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.WebSocket
import scala.concurrent.{ExecutionContext, Future}

import sttp.client3.FollowRedirectsBackend.UriEncoder

class OkHttpFutureBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int]
)(implicit
    ec: ExecutionContext
) extends OkHttpAsyncBackend[Future, Nothing, WebSockets](client, new FutureMonad, closeClient, customEncodingHandler) {
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
    override def streamToRequestBody(stream: Nothing): OkHttpRequestBody = stream
  }
}

object OkHttpFutureBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int],
      uriEncoder: UriEncoder
  )(implicit
      ec: ExecutionContext
  ): SttpBackend[Future, WebSockets] =
    new FollowRedirectsBackend(
      new OkHttpFutureBackend(client, closeClient, customEncodingHandler, webSocketBufferCapacity),
      uriEncoder = uriEncoder
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    OkHttpFutureBackend(
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
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    OkHttpFutureBackend(client, closeClient = false, customEncodingHandler, webSocketBufferCapacity, uriEncoder)

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackendStub[Future, WebSockets] = SttpBackendStub.asynchronousFuture
}
