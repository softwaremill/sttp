package sttp.client4.asynchttpclient.scalaz

import org.asynchttpclient._
import scalaz.concurrent.Task
import sttp.client4.asynchttpclient.AsyncHttpClientBackend
import sttp.client4.impl.scalaz.TaskMonadAsyncError
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.BackendStub
import sttp.client4.{wrappers, Backend, BackendOptions}

class AsyncHttpClientScalazBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[Task, Any](
      asyncHttpClient,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    throw new IllegalStateException("Web sockets are not supported!")
}

object AsyncHttpClientScalazBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): Backend[Task] =
    wrappers.FollowRedirectsBackend(new AsyncHttpClientScalazBackend(asyncHttpClient, closeClient, customizeRequest))

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[Backend[Task]] =
    Task.delay(
      AsyncHttpClientScalazBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[Backend[Task]] =
    Task.delay(AsyncHttpClientScalazBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[Backend[Task]] =
    Task.delay(
      AsyncHttpClientScalazBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Backend[Task] =
    AsyncHttpClientScalazBackend(client, closeClient = false, customizeRequest)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and doesn't support streaming.
    *
    * See [[BackendStub]] for details on how to configure stub responses.
    */
  def stub: BackendStub[Task] = BackendStub(TaskMonadAsyncError)
}
