package com.softwaremill.sttp.asynchttpclient.future

import java.nio.ByteBuffer

import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientHandler
import com.softwaremill.sttp.{FollowRedirectsHandler, FutureMonad, SttpHandler}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class AsyncHttpClientFutureHandler private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean)(implicit ec: ExecutionContext)
    extends AsyncHttpClientHandler[Future, Nothing](asyncHttpClient,
                                                    new FutureMonad,
                                                    closeClient) {

  override protected def streamBodyToPublisher(
      s: Nothing): Publisher[ByteBuffer] = s // nothing is everything

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This handler does not support streaming")

  override protected def publisherToString(
      p: Publisher[ByteBuffer]): Future[String] =
    throw new IllegalStateException("This handler does not support streaming")
}

object AsyncHttpClientFutureHandler {

  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit ec: ExecutionContext): SttpHandler[Future, Nothing] =
    new FollowRedirectsHandler[Future, Nothing](
      new AsyncHttpClientFutureHandler(asyncHttpClient, closeClient))

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply(connectionTimeout: FiniteDuration = SttpHandler.DefaultConnectionTimeout)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    AsyncHttpClientFutureHandler(
      new DefaultAsyncHttpClient(
        AsyncHttpClientHandler.withConnectionTimeout(connectionTimeout)),
      closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfig(cfg: AsyncHttpClientConfig)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    AsyncHttpClientFutureHandler(new DefaultAsyncHttpClient(cfg),
                                 closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingClient(client: AsyncHttpClient)(implicit ec: ExecutionContext =
                                             ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    AsyncHttpClientFutureHandler(client, closeClient = false)
}
