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

class AsyncHttpClientZioBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)
    extends AsyncHttpClientBackend[Task, Stream[Throwable, ByteBuffer]](asyncHttpClient, IOMonadAsyncError, closeClient) {

  val runtime = new DefaultRuntime {}

  private val bufferSize = 10 //TODO what should we set here?

  override protected def streamBodyToPublisher(s: Stream[Throwable, ByteBuffer]): Publisher[ByteBuf] =
    runtime.unsafeRunSync(s.map(Unpooled.wrappedBuffer).toPublisher).toEither.right.get

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[Throwable, ByteBuffer] =
    p.toStream(bufferSize)

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): IO[Throwable, Array[Byte]] =
    p.toStream(bufferSize).foldLeft(ByteBuffer.allocate(0))(concatByteBuffers).map(_.array())
}

object AsyncHttpClientZioBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    new FollowRedirectsBackend[Task, Stream[Throwable, ByteBuffer]](
      new AsyncHttpClientZioBackend(asyncHttpClient, closeClient)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  def usingConfig(cfg: AsyncHttpClientConfig): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(new DefaultAsyncHttpClient(cfg), closeClient = true)

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true
    )

  def usingClient(client: AsyncHttpClient): SttpBackend[Task, Stream[Throwable, ByteBuffer]] =
    AsyncHttpClientZioBackend(client, closeClient = false)
}
