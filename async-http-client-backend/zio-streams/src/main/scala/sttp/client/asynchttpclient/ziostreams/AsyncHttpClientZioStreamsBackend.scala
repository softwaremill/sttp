package sttp.client.asynchttpclient.ziostreams

import java.io.{File, FileOutputStream}
import java.nio.ByteBuffer

import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, WebSocketHandler}
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.internal._
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._
import zio.blocking.Blocking
import zio.interop.reactiveStreams._
import zio.stream._

class AsyncHttpClientZioStreamsBackend[R] private (
    runtime: Runtime[R],
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[Task, Stream[Throwable, ByteBuffer]](
      asyncHttpClient,
      TaskMonadAsyncError,
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
      .flatMap { os => p.toStream(bufferSize).map(b => Chunk.fromArray(b.array())).run(ZSink.fromOutputStream(os)) }
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

  def apply[R](
      runtime: Runtime[R],
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    Task.effect(
      AsyncHttpClientZioStreamsBackend(
        runtime,
        AsyncHttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest
      )
    )

  def usingConfig[R](
      runtime: Runtime[R],
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    Task.effect(
      AsyncHttpClientZioStreamsBackend(runtime, new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest)
    )

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[R](
      runtime: Runtime[R],
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler]] =
    Task.effect(
      AsyncHttpClientZioStreamsBackend(
        runtime,
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  def usingClient[R](
      runtime: Runtime[R],
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer], WebSocketHandler] =
    AsyncHttpClientZioStreamsBackend(runtime, client, closeClient = false, customizeRequest)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports
    * `Stream[Throwable, ByteBuffer]` streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, Stream[Throwable, ByteBuffer]] = SttpBackendStub(TaskMonadAsyncError)
}
