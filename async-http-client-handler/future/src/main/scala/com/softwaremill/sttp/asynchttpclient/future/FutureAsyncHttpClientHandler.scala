package com.softwaremill.sttp.asynchttpclient.future

import com.softwaremill.sttp.asynchttpclient.{
  AsyncHttpClientHandler,
  WrapperFromAsync
}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}

import scala.concurrent.{Future, Promise}

class FutureAsyncHttpClientHandler(asyncHttpClient: AsyncHttpClient)
    extends AsyncHttpClientHandler[Future](asyncHttpClient, FutureFromAsync) {

  def this() = this(new DefaultAsyncHttpClient())
  def this(cfg: AsyncHttpClientConfig) = this(new DefaultAsyncHttpClient(cfg))
}

private[asynchttpclient] object FutureFromAsync
    extends WrapperFromAsync[Future] {
  override def apply[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Future[T] = {
    val p = Promise[T]()
    register {
      case Left(t)  => p.failure(t)
      case Right(t) => p.success(t)
    }
    p.future
  }
}
