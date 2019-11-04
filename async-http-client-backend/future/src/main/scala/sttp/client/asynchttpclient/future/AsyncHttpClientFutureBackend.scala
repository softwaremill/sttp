package sttp.client.asynchttpclient.future

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
import sttp.client.monad.FutureMonad
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

import scala.concurrent.{ExecutionContext, Future}

class AsyncHttpClientFutureBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
)(
    implicit ec: ExecutionContext
) extends AsyncHttpClientBackend[Future, Nothing](asyncHttpClient, new FutureMonad, closeClient, customizeRequest) {
  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientFutureBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  )(
      implicit ec: ExecutionContext
  ): SttpBackend[Future, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Future, Nothing, WebSocketHandler](
      new AsyncHttpClientFutureBackend(asyncHttpClient, closeClient, customizeRequest)
    )

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing, WebSocketHandler] =
    AsyncHttpClientFutureBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing, WebSocketHandler] =
    AsyncHttpClientFutureBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest)

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing, WebSocketHandler] =
    AsyncHttpClientFutureBackend(
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true,
      customizeRequest
    )

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing, WebSocketHandler] =
    AsyncHttpClientFutureBackend(client, closeClient = false, customizeRequest)
}
