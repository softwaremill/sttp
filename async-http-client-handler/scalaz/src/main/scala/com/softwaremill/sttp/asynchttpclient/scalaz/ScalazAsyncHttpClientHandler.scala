package com.softwaremill.sttp.asynchttpclient.scalaz

import com.softwaremill.sttp.asynchttpclient.internal.{
  AsyncHttpClientHandler,
  WrapperFromAsync
}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}

import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

class ScalazAsyncHttpClientHandler(asyncHttpClient: AsyncHttpClient)
    extends AsyncHttpClientHandler[Task](asyncHttpClient, TaskFromAsync) {

  def this() = this(new DefaultAsyncHttpClient())
  def this(cfg: AsyncHttpClientConfig) = this(new DefaultAsyncHttpClient(cfg))
}

private[asynchttpclient] object TaskFromAsync extends WrapperFromAsync[Task] {
  override def apply[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Task[T] =
    Task.async { cb =>
      register {
        case Left(t)  => cb(-\/(t))
        case Right(t) => cb(\/-(t))
      }
    }
}
