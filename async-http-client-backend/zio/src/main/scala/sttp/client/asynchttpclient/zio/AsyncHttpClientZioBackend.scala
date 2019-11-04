package sttp.client.asynchttpclient.zio

import java.nio.ByteBuffer

import io.netty.buffer.ByteBuf
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
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._

class AsyncHttpClientZioBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[Task, Nothing](asyncHttpClient, TaskMonadAsyncError, closeClient, customizeRequest) {
  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientZioBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): SttpBackend[Task, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Task, Nothing, WebSocketHandler](
      new AsyncHttpClientZioBackend(asyncHttpClient, closeClient, customizeRequest)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Nothing, WebSocketHandler]] =
    Task.effect(
      AsyncHttpClientZioBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
    )

  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Nothing, WebSocketHandler]] =
    Task.effect(AsyncHttpClientZioBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Task[SttpBackend[Task, Nothing, WebSocketHandler]] =
    Task.effect(
      AsyncHttpClientZioBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): SttpBackend[Task, Nothing, WebSocketHandler] =
    AsyncHttpClientZioBackend(client, closeClient = false, customizeRequest)
}
