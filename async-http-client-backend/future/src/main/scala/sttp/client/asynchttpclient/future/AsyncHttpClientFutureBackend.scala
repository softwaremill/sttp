package sttp.client.asynchttpclient.future

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
import sttp.client.monad.FutureMonad
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

import scala.concurrent.{ExecutionContext, Future}

class AsyncHttpClientFutureBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
    implicit ec: ExecutionContext
) extends AsyncHttpClientBackend[Future, Nothing](asyncHttpClient, new FutureMonad, closeClient) {

  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientFutureBackend {

  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit ec: ExecutionContext
  ): SttpBackend[Future, Nothing] =
    new FollowRedirectsBackend[Future, Nothing](new AsyncHttpClientFutureBackend(asyncHttpClient, closeClient))

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing] =
    AsyncHttpClientFutureBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfig(
      cfg: AsyncHttpClientConfig
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing] =
    AsyncHttpClientFutureBackend(new DefaultAsyncHttpClient(cfg), closeClient = true)

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing] =
    AsyncHttpClientFutureBackend(
      AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
      closeClient = true
    )

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingClient(
      client: AsyncHttpClient
  )(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[Future, Nothing] =
    AsyncHttpClientFutureBackend(client, closeClient = false)
}
