package sttp.client4.asynchttpclient.zio

import _root_.zio._
import org.asynchttpclient._
import sttp.capabilities.WebSockets
import sttp.client4.asynchttpclient.AsyncHttpClientBackend
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioSimpleQueue}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.{wrappers, BackendOptions, WebSocketBackend}

class AsyncHttpClientZioBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
    webSocketBufferCapacity: Option[Int]
) extends AsyncHttpClientBackend[Task, WebSockets](
      asyncHttpClient,
      new RIOMonadAsyncError[Any],
      closeClient,
      customizeRequest
    )
    with WebSocketBackend[Task] {

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- webSocketBufferCapacity match {
        case Some(capacity) => Queue.dropping[T](capacity)
        case None           => Queue.unbounded[T]
      }
    } yield new ZioSimpleQueue(queue, runtime)
}

object AsyncHttpClientZioBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      webSocketBufferCapacity: Option[Int]
  ): WebSocketBackend[Task] =
    wrappers.FollowRedirectsBackend(
      new AsyncHttpClientZioBackend(asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): UIO[WebSocketBackend[Task]] =
    ZIO.effectTotal(
      AsyncHttpClientZioBackend(
        AsyncHttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  def managed(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): UManaged[WebSocketBackend[Task]] =
    ZManaged.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close().ignore)

  def layer(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.fromManaged(managed(options, customizeRequest, webSocketBufferCapacity))

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): UIO[WebSocketBackend[Task]] =
    ZIO.effectTotal(
      AsyncHttpClientZioBackend(
        new DefaultAsyncHttpClient(cfg),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  def managedUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): UManaged[WebSocketBackend[Task]] =
    ZManaged.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close().ignore)

  def layerUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.fromManaged(managedUsingConfig(cfg, customizeRequest, webSocketBufferCapacity))

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): UIO[WebSocketBackend[Task]] =
    ZIO.effectTotal(
      AsyncHttpClientZioBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def managedUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): UManaged[WebSocketBackend[Task]] =
    ZManaged.make(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))(
      _.close().ignore
    )

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def layerUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.fromManaged(managedUsingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))

  def usingClient[R](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): WebSocketBackend[Task] =
    AsyncHttpClientZioBackend(client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  def layerUsingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.fromAcquireRelease(
      ZIO.effectTotal(usingClient(client, customizeRequest, webSocketBufferCapacity))
    )(
      _.close().ignore
    )

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Stream[Throwable,
    * ByteBuffer]` streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketBackendStub[Task] = WebSocketBackendStub(new RIOMonadAsyncError[Any])

  val stubLayer: Layer[Throwable, SttpClient with SttpClientStubbing] = SttpClientStubbing.layer
}
