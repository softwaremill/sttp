package sttp.client4.asynchttpclient.zio

import _root_.zio._
import _root_.zio.stream._
import org.asynchttpclient._
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client4.asynchttpclient.AsyncHttpClientBackend
import sttp.client4.httpclient.zio.SttpClient
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioSimpleQueue}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.{WebSocketBackendStub, WebSocketStreamBackendStub}
import sttp.client4.{wrappers, BackendOptions, WebSocketBackend, WebSocketStreamBackend}

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
  ): WebSocketBackend[Task] =
    AsyncHttpClientZioBackend(
      AsyncHttpClientBackend.defaultClient(options),
      closeClient = true,
      customizeRequest,
      webSocketBufferCapacity
    )

  def scoped(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): ZIO[Scope, Nothing, WebSocketBackend[Task]] =
    ZIO.acquireRelease(ZIO.succeed(apply(options, customizeRequest, webSocketBufferCapacity)))(_.close().ignore)

  def layer(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.scoped(scoped(options, customizeRequest, webSocketBufferCapacity))

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): WebSocketBackend[Task] =
    AsyncHttpClientZioBackend(
      new DefaultAsyncHttpClient(cfg),
      closeClient = true,
      customizeRequest,
      webSocketBufferCapacity
    )

  def scopedUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): ZIO[Scope, Nothing, WebSocketBackend[Task]] =
    ZIO.acquireRelease(ZIO.succeed(usingConfig(cfg, customizeRequest, webSocketBufferCapacity)))(_.close().ignore)

  def layerUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.scoped(scopedUsingConfig(cfg, customizeRequest, webSocketBufferCapacity))

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): WebSocketBackend[Task] =
    AsyncHttpClientZioBackend(
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true,
      customizeRequest,
      webSocketBufferCapacity
    )

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def scopedUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): ZIO[Scope, Nothing, WebSocketBackend[Task]] =
    ZIO.acquireRelease(
      ZIO.succeed(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))
    )(
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
    ZLayer.scoped(scopedUsingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))

  def usingClient(
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
    ZLayer.scoped(
      ZIO.acquireRelease(
        ZIO.succeed(usingClient(client, customizeRequest, webSocketBufferCapacity))
      )(
        _.close().ignore
      )
    )

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports
    * [[Stream[Throwable, ByteBuffer]]] streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketBackendStub[Task] = WebSocketBackendStub(new RIOMonadAsyncError[Any])
}
