package sttp.client.asynchttpclient.zio

import java.nio.ByteBuffer

import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.AsyncHttpClientBackend
import sttp.client.impl.zio.IOMonadAsyncError
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._

class AsyncHttpClientZioBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)
    extends AsyncHttpClientBackend[Task, Nothing](asyncHttpClient, IOMonadAsyncError, closeClient) {

  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientZioBackend {
  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean): SttpBackend[Task, Nothing] =
    new FollowRedirectsBackend[IO[Throwable, ?], Nothing](new AsyncHttpClientZioBackend(asyncHttpClient, closeClient))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default): Task[SttpBackend[Task, Nothing]] =
    Task.effect(AsyncHttpClientZioBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true))

  def usingConfig(cfg: AsyncHttpClientConfig): Task[SttpBackend[Task, Nothing]] =
    Task.effect(AsyncHttpClientZioBackend(new DefaultAsyncHttpClient(cfg), closeClient = true))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): Task[SttpBackend[Task, Nothing]] =
    Task.effect(
      AsyncHttpClientZioBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true
      )
    )

  def usingClient(client: AsyncHttpClient): SttpBackend[IO[Throwable, ?], Nothing] =
    AsyncHttpClientZioBackend(client, closeClient = false)
}
