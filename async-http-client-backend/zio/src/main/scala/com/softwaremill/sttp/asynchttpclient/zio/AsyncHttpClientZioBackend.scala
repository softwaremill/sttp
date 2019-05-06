package com.softwaremill.sttp.asynchttpclient.zio

import java.nio.ByteBuffer

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal._
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.impl.zio.IOMonadAsyncError
import com.softwaremill.sttp.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import io.netty.buffer.{ByteBuf, Unpooled}
import scalaz.zio._
import scalaz.zio.stream._
import scalaz.zio.interop.reactiveStreams._

class AsyncHttpClientZioBackend[R] private (runtime: Runtime[R], asyncHttpClient: AsyncHttpClient, closeClient: Boolean)
    extends AsyncHttpClientBackend[Task, Stream[Throwable, ByteBuffer]](asyncHttpClient, IOMonadAsyncError, closeClient) {

  private val bufferSize = 16

  override protected def streamBodyToPublisher(s: Stream[Throwable, ByteBuffer]): Publisher[ByteBuf] =
    runtime.unsafeRun(s.map(Unpooled.wrappedBuffer).toPublisher)

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[Throwable, ByteBuffer] =
    p.toStream(bufferSize)

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): IO[Throwable, Array[Byte]] =
    p.toStream(bufferSize).foldLeft(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array())
}

object AsyncHttpClientZioBackend {
  private def apply[R](
      runtime: Runtime[R],
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    new FollowRedirectsBackend[Task, Stream[Throwable, ByteBuffer]](
      new AsyncHttpClientZioBackend(runtime, asyncHttpClient, closeClient)
    )

  def apply[R](
      runtime: Runtime[R],
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(runtime, AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  def usingConfig[R](
      runtime: Runtime[R],
      cfg: AsyncHttpClientConfig
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(runtime, new DefaultAsyncHttpClient(cfg), closeClient = true)

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[R](
      runtime: Runtime[R],
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(
      runtime,
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true
    )

  def usingClient[R](runtime: Runtime[R], client: AsyncHttpClient): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(runtime, client, closeClient = false)
}
