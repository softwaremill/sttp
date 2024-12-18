package sttp.client4.asynchttpclient.monix

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import org.asynchttpclient._
import sttp.capabilities.WebSockets
import sttp.client4.asynchttpclient.AsyncHttpClientBackend
import sttp.client4.impl.monix.{MonixSimpleQueue, TaskMonadAsyncError}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.{wrappers, BackendOptions, WebSocketBackend}

class AsyncHttpClientMonixBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
    webSocketBufferCapacity: Option[Int]
)(implicit
    scheduler: Scheduler
) extends AsyncHttpClientBackend[Task, WebSockets](
      asyncHttpClient,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
    )
    with WebSocketBackend[Task] {

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](webSocketBufferCapacity))
}

object AsyncHttpClientMonixBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      webSocketBufferCapacity: Option[Int]
  )(implicit
      scheduler: Scheduler
  ): WebSocketBackend[Task] =
    wrappers.FollowRedirectsBackend(
      new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity)
    )

  /** @param s The scheduler used for handling web socket bodies. Defaults to the global scheduler. */
  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketBackend[Task]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param s
    *   The scheduler used for handling web socket bodies. Defaults to the global scheduler.
    */
  def resource(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketBackend[Task]] =
    Resource.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param s The scheduler used for handling web socket bodies. Defaults to the global scheduler. */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketBackend[Task]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        new DefaultAsyncHttpClient(cfg),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param s
    *   The scheduler used for handling web socket bodies. Defaults to the global scheduler.
    */
  def resourceUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketBackend[Task]] =
    Resource.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    * @param s
    *   The scheduler used for handling web socket bodies. Defaults to the global scheduler.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketBackend[Task]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    * @param s
    *   The scheduler used for handling web socket bodies. Defaults to the global scheduler.
    */
  def resourceUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketBackend[Task]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param s The scheduler used for handling web socket bodies. Defaults to the global scheduler. */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit s: Scheduler = Scheduler.global): WebSocketBackend[Task] =
    AsyncHttpClientMonixBackend(client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[BackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketBackend[Task] = WebSocketBackendStub(TaskMonadAsyncError)
}
