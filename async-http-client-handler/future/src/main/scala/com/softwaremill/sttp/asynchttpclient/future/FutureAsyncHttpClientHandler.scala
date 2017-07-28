package com.softwaremill.sttp.asynchttpclient.future

import java.nio.ByteBuffer

import com.softwaremill.sttp.SttpHandler
import com.softwaremill.sttp.asynchttpclient.{
  AsyncHttpClientHandler,
  MonadAsyncError
}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future, Promise}

class FutureAsyncHttpClientHandler private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean)(implicit ec: ExecutionContext)
    extends AsyncHttpClientHandler[Future, Nothing](asyncHttpClient,
                                                    new FutureMonad(),
                                                    closeClient) {

  override protected def streamBodyToPublisher(
      s: Nothing): Publisher[ByteBuffer] = s // nothing is everything

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This handler does not support streaming")
}

object FutureAsyncHttpClientHandler {

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply()(implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    new FutureAsyncHttpClientHandler(new DefaultAsyncHttpClient(),
                                     closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfig(cfg: AsyncHttpClientConfig)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    new FutureAsyncHttpClientHandler(new DefaultAsyncHttpClient(),
                                     closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingClient(client: AsyncHttpClient)(implicit ec: ExecutionContext =
                                             ExecutionContext.Implicits.global)
    : SttpHandler[Future, Nothing] =
    new FutureAsyncHttpClientHandler(client, closeClient = false)
}

private[future] class FutureMonad(implicit ec: ExecutionContext)
    extends MonadAsyncError[Future] {
  override def unit[T](t: T): Future[T] = Future.successful(t)

  override def map[T, T2](fa: Future[T], f: (T) => T2): Future[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Future[T], f: (T) => Future[T2]): Future[T2] =
    fa.flatMap(f)

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Future[T] = {
    val p = Promise[T]()
    register {
      case Left(t)  => p.failure(t)
      case Right(t) => p.success(t)
    }
    p.future
  }

  override def error[T](t: Throwable): Future[T] = Future.failed(t)
}
