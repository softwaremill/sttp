package sttp.client4.asynchttpclient.future

import org.asynchttpclient._
import sttp.client4.asynchttpclient.AsyncHttpClientBackend
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.BackendStub
import sttp.client4.{wrappers, Backend, BackendOptions}
import sttp.monad.FutureMonad

import scala.concurrent.{ExecutionContext, Future}

class AsyncHttpClientFutureBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
)(implicit
    ec: ExecutionContext
) extends AsyncHttpClientBackend[Future, Any](
      asyncHttpClient,
      new FutureMonad,
      closeClient,
      customizeRequest
    ) {

  override protected def createSimpleQueue[T]: Future[SimpleQueue[Future, T]] =
    throw new IllegalStateException("Web sockets are not supported!")
}

object AsyncHttpClientFutureBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  )(implicit
      ec: ExecutionContext
  ): Backend[Future] =
    wrappers.FollowRedirectsBackend(new AsyncHttpClientFutureBackend(asyncHttpClient, closeClient, customizeRequest))

  /** @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the global
    *   execution context.
    */
  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.global): Backend[Future] =
    AsyncHttpClientFutureBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)

  /** @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the global
    *   execution context.
    */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.global): Backend[Future] =
    AsyncHttpClientFutureBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest)

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    * @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the global
    *   execution context.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.global): Backend[Future] =
    AsyncHttpClientFutureBackend(
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true,
      customizeRequest
    )

  /** @param ec
    *   The execution context for running non-network related operations, e.g. mapping responses. Defaults to the global
    *   execution context.
    */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.global): Backend[Future] =
    AsyncHttpClientFutureBackend(client, closeClient = false, customizeRequest)

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[BackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): BackendStub[Future] =
    BackendStub(new FutureMonad())
}
