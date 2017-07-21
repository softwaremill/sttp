package com.softwaremill.sttp.asynchttpclient.monix

import com.softwaremill.sttp.asynchttpclient.{
  AsyncHttpClientHandler,
  WrapperFromAsync
}
import monix.eval.Task
import monix.execution.Cancelable
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}

import scala.util.{Failure, Success}

class MonixAsyncHttpClientHandler(asyncHttpClient: AsyncHttpClient)
    extends AsyncHttpClientHandler[Task](asyncHttpClient, TaskFromAsync) {

  def this() = this(new DefaultAsyncHttpClient())
  def this(cfg: AsyncHttpClientConfig) = this(new DefaultAsyncHttpClient(cfg))
}

private[asynchttpclient] object TaskFromAsync extends WrapperFromAsync[Task] {
  override def apply[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Task[T] =
    Task.async { (_, cb) =>
      register {
        case Left(t)  => cb(Failure(t))
        case Right(t) => cb(Success(t))
      }

      Cancelable.empty
    }
}
