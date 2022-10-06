package sttp.client3.asynchttpclient.cats

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer

import cats.effect.kernel.{Async, Resource, Sync}
import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client3.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.internal.{FileHelpers, NoStreams}
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import cats.implicits._
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadAsyncError
import sttp.ws.WebSocket

class AsyncHttpClientCatsBackend[F[_]: Async] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[F, Nothing, Any](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: NoStreams = NoStreams

  override protected val bodyFromAHC: BodyFromAHC[F, Nothing] = new BodyFromAHC[F, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError
    override def publisherToStream(p: Publisher[ByteBuffer]): Nothing =
      throw new IllegalStateException("This backend does not support streaming")
    override def compileWebSocketPipe(ws: WebSocket[F], pipe: Nothing): F[Unit] = pipe // nothing is everything

    override def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
      publisherToBytes(p)
        .map(bytes => FileHelpers.saveFile(f, new ByteArrayInputStream(bytes)))
    }
  }

  override protected def bodyToAHC: BodyToAHC[F, Nothing] =
    new BodyToAHC[F, Nothing] {
      override val streams: NoStreams = NoStreams
      override protected def streamToPublisher(s: Nothing): Publisher[ByteBuf] = s // nothing is everything
    }

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    throw new IllegalStateException("Web sockets are not supported!")
}

@deprecated(message = "The async-http-client project is no longer maintained")
object AsyncHttpClientCatsBackend {
  private def apply[F[_]: Async](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): SttpBackend[F, Any] =
    new FollowRedirectsBackend[F, Any](
      new AsyncHttpClientCatsBackend(asyncHttpClient, closeClient, customizeRequest)
    )

  def apply[F[_]: Async](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Any]] =
    Sync[F].delay(
      AsyncHttpClientCatsBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  /** Makes sure the backend is closed after usage. */
  def resource[F[_]: Async](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.make(apply(options, customizeRequest))(_.close())

  def usingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Any]] =
    Sync[F].delay(AsyncHttpClientCatsBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /** Makes sure the backend is closed after usage. */
  def resourceUsingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.make(usingConfig(cfg, customizeRequest))(_.close())

  /** @param updateConfig A function which updates the default configuration (created basing on `options`). */
  def usingConfigBuilder[F[_]: Async](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Any]] =
    Sync[F].delay(
      AsyncHttpClientCatsBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def resourceUsingConfigBuilder[F[_]: Async](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest))(_.close())

  def usingClient[F[_]: Async](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): SttpBackend[F, Any] =
    AsyncHttpClientCatsBackend(client, closeClient = false, customizeRequest)

  /** Create a stub backend for testing, which uses the `F` response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: SttpBackendStub[F, Any] = SttpBackendStub(new CatsMonadAsyncError())
}
