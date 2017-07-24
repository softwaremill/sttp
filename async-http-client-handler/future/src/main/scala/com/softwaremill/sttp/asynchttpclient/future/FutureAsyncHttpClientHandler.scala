package com.softwaremill.sttp.asynchttpclient.future

import com.softwaremill.sttp.asynchttpclient.{
  AsyncHttpClientHandler,
  MonadAsyncError
}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}

import scala.concurrent.{ExecutionContext, Future, Promise}

class FutureAsyncHttpClientHandler(asyncHttpClient: AsyncHttpClient)(
    implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    extends AsyncHttpClientHandler[Future](asyncHttpClient, new FutureMonad()) {

  def this() = this(new DefaultAsyncHttpClient())
  def this(cfg: AsyncHttpClientConfig) = this(new DefaultAsyncHttpClient(cfg))
}

private[future] class FutureMonad(implicit ec: ExecutionContext)
    extends MonadAsyncError[Future] {
  override def unit[T](t: T): Future[T] = Future.successful(t)

  override def map[T, T2](fa: Future[T], f: (T) => T2): Future[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Future[T],
                              f: (T) => Future[T2]): Future[T2] =
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
