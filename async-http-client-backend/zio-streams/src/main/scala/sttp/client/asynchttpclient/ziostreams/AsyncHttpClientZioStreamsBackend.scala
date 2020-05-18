package sttp.client.asynchttpclient.ziostreams

import java.io.{File, FileOutputStream}
import java.nio.ByteBuffer

import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{AsyncHttpClient, AsyncHttpClientConfig, BoundRequestBuilder, DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, WebSocketHandler}
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.internal._
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._
import zio.blocking.Blocking
import zio.interop.reactivestreams._
import zio.stream._

class AsyncHttpClientZioStreamsBackend[R] private (
    runtime: Runtime[R],
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[Task, Stream[Throwable, ByteBuffer]](
      asyncHttpClient,
      new RIOMonadAsyncError,
      closeClient,
      customizeRequest
    ) {
  private val bufferSize = 16

  override protected def streamBodyToPublisher(s: Stream[Throwable, ByteBuffer]): Publisher[ByteBuf] =
    runtime.unsafeRun(s.map(Unpooled.wrappedBuffer).toPublisher)

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[Throwable, ByteBuffer] =
    p.toStream(bufferSize)

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] =
    p.toStream(bufferSize).fold(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array())

  override protected def publisherToFile(p: Publisher[ByteBuffer], f: File): Task[Unit] = {
    blocking
      .effectBlocking(new FileOutputStream(f))
      .flatMap { os =>
        p.toStream(bufferSize).map(b => Chunk.fromArray(b.array())).flattenChunks.run(ZSink.fromOutputStream(os))
      }
      .unit
      .provideLayer(Blocking.live)
  }
}

object AsyncHttpClientZioStreamsBackend {
  private def apply[R](
      runtime: Runtime[R],
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler] =
    new FollowRedirectsBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler](
      new AsyncHttpClientZioStreamsBackend(runtime, asyncHttpClient, closeClient, customizeRequest)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    ZIO
      .runtime[Any]
      .flatMap(runtime =>
        Task.effect(
          AsyncHttpClientZioStreamsBackend(
            runtime,
            AsyncHttpClientBackend.defaultClient(options),
            closeClient = true,
            customizeRequest
          )
        )
      )

  def managed(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): TaskManaged[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    ZManaged.make(apply(options, customizeRequest))(_.close().ignore)

  def layer(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Layer[Throwable, SttpStreamsClient] =
    ZLayer.fromManaged(managed(options, customizeRequest))

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    ZIO
      .runtime[Any]
      .flatMap(runtime =>
        Task.effect(
          AsyncHttpClientZioStreamsBackend(
            runtime,
            new DefaultAsyncHttpClient(cfg),
            closeClient = true,
            customizeRequest
          )
        )
      )

  def managedUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): TaskManaged[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    ZManaged.make(usingConfig(cfg, customizeRequest))(_.close().ignore)

  def layerUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Layer[Throwable, SttpStreamsClient] =
    ZLayer.fromManaged(managedUsingConfig(cfg, customizeRequest))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    ZIO
      .runtime[Any]
      .flatMap(runtime =>
        Task.effect(
          AsyncHttpClientZioStreamsBackend(
            runtime,
            AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
            closeClient = true,
            customizeRequest
          )
        )
      )

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def managedUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): TaskManaged[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    ZManaged.make(usingConfigBuilder(updateConfig, options, customizeRequest))(_.close().ignore)

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def layerUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Layer[Throwable, SttpStreamsClient] =
    ZLayer.fromManaged(managedUsingConfigBuilder(updateConfig, options, customizeRequest))

  def usingClient[R](
      runtime: Runtime[R],
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler] =
    AsyncHttpClientZioStreamsBackend(runtime, client, closeClient = false, customizeRequest)

  def layerUsingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Layer[Nothing, SttpStreamsClient] =
    ZLayer.fromAcquireRelease(UIO.runtime.map(runtime => usingClient(runtime, client, customizeRequest)))(
      _.close().ignore
    )

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports
    * `Stream[Throwable, ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, Stream[Throwable, ByteBuffer], WebSocketHandler] = SttpBackendStub(new RIOMonadAsyncError)
}
