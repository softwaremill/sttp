package sttp.client.asynchttpclient.ziostreams

import java.nio.ByteBuffer

import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.AsyncHttpClientBackend
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.internal._
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._
import zio.interop.reactiveStreams._
import zio.stream._

class AsyncHttpClientZioStreamsBackend[R] private (
    runtime: Runtime[R],
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean
) extends AsyncHttpClientBackend[Task, Stream[Throwable, ByteBuffer]](asyncHttpClient, TaskMonadAsyncError, closeClient) {

  private val bufferSize = 16

  override protected def streamBodyToPublisher(s: Stream[Throwable, ByteBuffer]): Publisher[ByteBuf] =
    runtime.unsafeRun(s.map(Unpooled.wrappedBuffer).toPublisher)

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[Throwable, ByteBuffer] =
    p.toStream(bufferSize)

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] =
    p.toStream(bufferSize).foldLeft(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array())
}

object AsyncHttpClientZioStreamsBackend {
  private def apply[R](
      runtime: Runtime[R],
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    new FollowRedirectsBackend[Task, Stream[Throwable, ByteBuffer]](
      new AsyncHttpClientZioStreamsBackend(runtime, asyncHttpClient, closeClient)
    )

  def apply[R](
      runtime: Runtime[R],
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer]]] =
    Task.effect(
      AsyncHttpClientZioStreamsBackend(runtime, AsyncHttpClientBackend.defaultClient(options), closeClient = true)
    )

  def usingConfig[R](
      runtime: Runtime[R],
      cfg: AsyncHttpClientConfig
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer]]] =
    Task.effect(AsyncHttpClientZioStreamsBackend(runtime, new DefaultAsyncHttpClient(cfg), closeClient = true))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[R](
      runtime: Runtime[R],
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): Task[SttpBackend[Task, Stream[Throwable, ByteBuffer]]] =
    Task.effect(
      AsyncHttpClientZioStreamsBackend(
        runtime,
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true
      )
    )

  def usingClient[R](runtime: Runtime[R], client: AsyncHttpClient): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioStreamsBackend(runtime, client, closeClient = false)
}
