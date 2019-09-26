package sttp.client.asynchttpclient.scalaz

import java.nio.ByteBuffer

import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import scalaz.concurrent.Task
import sttp.client.asynchttpclient.AsyncHttpClientBackend
import sttp.client.impl.scalaz.TaskMonadAsyncError
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

class AsyncHttpClientScalazBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)
    extends AsyncHttpClientBackend[Task, Nothing](asyncHttpClient, TaskMonadAsyncError, closeClient) {

  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientScalazBackend {
  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean): SttpBackend[Task, Nothing] =
    new FollowRedirectsBackend[Task, Nothing](new AsyncHttpClientScalazBackend(asyncHttpClient, closeClient))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default): Task[SttpBackend[Task, Nothing]] =
    Task.delay(AsyncHttpClientScalazBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true))

  def usingConfig(cfg: AsyncHttpClientConfig): Task[SttpBackend[Task, Nothing]] =
    Task.delay(AsyncHttpClientScalazBackend(new DefaultAsyncHttpClient(cfg), closeClient = true))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): Task[SttpBackend[Task, Nothing]] =
    Task.delay(
      AsyncHttpClientScalazBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true
      )
    )

  def usingClient(client: AsyncHttpClient): SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(client, closeClient = false)
}
