package sttp.client4.asynchttpclient.cats

import cats.effect.kernel.{Async, Resource, Sync}
import org.asynchttpclient._
import sttp.client4.asynchttpclient.AsyncHttpClientBackend
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.BackendStub
import sttp.client4.{wrappers, Backend, BackendOptions}

class AsyncHttpClientCatsBackend[F[_]: Async] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[F, Any](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient,
      customizeRequest
    ) {
  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    throw new IllegalStateException("Web sockets are not supported!")
}

object AsyncHttpClientCatsBackend {
  private def apply[F[_]: Async](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): Backend[F] =
    wrappers.FollowRedirectsBackend(new AsyncHttpClientCatsBackend(asyncHttpClient, closeClient, customizeRequest))

  def apply[F[_]: Async](
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[Backend[F]] =
    Sync[F].delay(
      AsyncHttpClientCatsBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  /** Makes sure the backend is closed after usage. */
  def resource[F[_]: Async](
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, Backend[F]] =
    Resource.make(apply(options, customizeRequest))(_.close())

  def usingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[Backend[F]] =
    Sync[F].delay(AsyncHttpClientCatsBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /** Makes sure the backend is closed after usage. */
  def resourceUsingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, Backend[F]] =
    Resource.make(usingConfig(cfg, customizeRequest))(_.close())

  /** @param updateConfig A function which updates the default configuration (created basing on `options`). */
  def usingConfigBuilder[F[_]: Async](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[Backend[F]] =
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
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, Backend[F]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest))(_.close())

  def usingClient[F[_]: Async](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Backend[F] =
    AsyncHttpClientCatsBackend(client, closeClient = false, customizeRequest)

  /** Create a stub backend for testing, which uses the `F` response wrapper, and doesn't support streaming.
    *
    * See [[BackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: BackendStub[F] = BackendStub(new CatsMonadAsyncError())
}
