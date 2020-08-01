package sttp.client.asynchttpclient.zio

import java.io.{File, FileOutputStream}
import java.nio.ByteBuffer

import _root_.zio._
import _root_.zio.blocking.Blocking
import _root_.zio.interop.reactivestreams.{
  publisherToStream => publisherToZioStream,
  streamToPublisher => zioStreamToPublisher
}
import _root_.zio.stream._
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client.impl.zio.{RIOMonadAsyncError, ZioAsyncQueue, ZioStreams, ZioWebSockets}
import sttp.client.internal._
import sttp.client.monad.MonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.AsyncQueue
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions, WebSockets}
import sttp.model.ws.WebSocketFrame

class AsyncHttpClientZioBackend private (
    runtime: Runtime[Any],
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
    webSocketBufferCapacity: Option[Int]
) extends AsyncHttpClientBackend[Task, ZioStreams, ZioStreams with WebSockets](
      asyncHttpClient,
      new RIOMonadAsyncError[Any],
      closeClient,
      customizeRequest
    ) {

  override val streams: ZioStreams = ZioStreams

  private val bufferSize = 16

  override protected val bodyFromAHC: BodyFromAHC[Task, ZioStreams] =
    new BodyFromAHC[Task, ZioStreams] {
      override val streams: ZioStreams = ZioStreams
      override implicit val monad: MonadAsyncError[Task] = new RIOMonadAsyncError[Any]

      override def publisherToStream(p: Publisher[ByteBuffer]): Stream[Throwable, Byte] =
        p.toStream(bufferSize).mapConcatChunk(Chunk.fromByteBuffer(_))

      override def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] =
        p.toStream(bufferSize).fold(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array())

      override def publisherToFile(p: Publisher[ByteBuffer], f: File): Task[Unit] = {
        p.toStream(bufferSize)
          .map(Chunk.fromByteBuffer)
          .flattenChunks
          .run(ZSink.fromOutputStream(new FileOutputStream(f)))
          .unit
          .provideLayer(Blocking.live)
      }

      override def compileWebSocketPipe(
          ws: WebSocket[Task],
          pipe: Transducer[Throwable, WebSocketFrame.Data[_], WebSocketFrame]
      ): Task[Unit] = ZioWebSockets.compilePipe(ws, pipe)
    }

  override protected val bodyToAHC: BodyToAHC[Task, ZioStreams] = new BodyToAHC[Task, ZioStreams] {
    override val streams: ZioStreams = ZioStreams

    override protected def streamToPublisher(s: Stream[Throwable, Byte]): Publisher[ByteBuf] =
      runtime.unsafeRun(s.mapChunks(c => Chunk.single(Unpooled.wrappedBuffer(c.toArray))).toPublisher)
  }

  override protected def createAsyncQueue[T]: Task[AsyncQueue[Task, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- webSocketBufferCapacity match {
        case Some(capacity) => Queue.dropping[T](capacity)
        case None           => Queue.unbounded[T]
      }
    } yield new ZioAsyncQueue(queue, runtime)
}

object AsyncHttpClientZioBackend {
  private def apply[R](
      runtime: Runtime[R],
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      webSocketBufferCapacity: Option[Int]
  ): SttpBackend[Task, ZioStreams with WebSockets] =
    new FollowRedirectsBackend(
      new AsyncHttpClientZioBackend(runtime, asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Task[SttpBackend[Task, ZioStreams with WebSockets]] =
    ZIO
      .runtime[Any]
      .flatMap(runtime =>
        Task.effect(
          AsyncHttpClientZioBackend(
            runtime,
            AsyncHttpClientBackend.defaultClient(options),
            closeClient = true,
            customizeRequest,
            webSocketBufferCapacity
          )
        )
      )

  def managed(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): TaskManaged[SttpBackend[Task, ZioStreams with WebSockets]] =
    ZManaged.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close().ignore)

  def layer(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Throwable, SttpClient] =
    ZLayer.fromManaged(managed(options, customizeRequest, webSocketBufferCapacity))

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Task[SttpBackend[Task, ZioStreams with WebSockets]] =
    ZIO
      .runtime[Any]
      .flatMap(runtime =>
        Task.effect(
          AsyncHttpClientZioBackend(
            runtime,
            new DefaultAsyncHttpClient(cfg),
            closeClient = true,
            customizeRequest,
            webSocketBufferCapacity
          )
        )
      )

  def managedUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): TaskManaged[SttpBackend[Task, ZioStreams with WebSockets]] =
    ZManaged.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close().ignore)

  def layerUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Throwable, SttpClient] =
    ZLayer.fromManaged(managedUsingConfig(cfg, customizeRequest, webSocketBufferCapacity))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Task[SttpBackend[Task, ZioStreams with WebSockets]] =
    ZIO
      .runtime[Any]
      .flatMap(runtime =>
        Task.effect(
          AsyncHttpClientZioBackend(
            runtime,
            AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
            closeClient = true,
            customizeRequest,
            webSocketBufferCapacity
          )
        )
      )

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def managedUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): TaskManaged[SttpBackend[Task, ZioStreams with WebSockets]] =
    ZManaged.make(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))(
      _.close().ignore
    )

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def layerUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Throwable, SttpClient] =
    ZLayer.fromManaged(managedUsingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))

  def usingClient[R](
      runtime: Runtime[R],
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): SttpBackend[Task, ZioStreams with WebSockets] =
    AsyncHttpClientZioBackend(runtime, client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  def layerUsingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Layer[Nothing, SttpClient] =
    ZLayer.fromAcquireRelease(
      UIO.runtime.map(runtime => usingClient(runtime, client, customizeRequest, webSocketBufferCapacity))
    )(
      _.close().ignore
    )

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports
    * `Stream[Throwable, ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, ZioStreams with WebSockets] =
    SttpBackendStub(new RIOMonadAsyncError[Any])
}
