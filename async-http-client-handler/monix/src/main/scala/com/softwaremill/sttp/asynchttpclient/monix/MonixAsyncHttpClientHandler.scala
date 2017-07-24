package com.softwaremill.sttp.asynchttpclient.monix

import com.softwaremill.sttp.asynchttpclient.{
  AsyncHttpClientHandler,
  MonadAsyncError
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
    extends AsyncHttpClientHandler[Task](asyncHttpClient, TaskMonad) {

  def this() = this(new DefaultAsyncHttpClient())
  def this(cfg: AsyncHttpClientConfig) = this(new DefaultAsyncHttpClient(cfg))
}

private[monix] object TaskMonad extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.now(t)

  override def map[T, T2](fa: Task[T], f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T], f: (T) => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Task[T] =
    Task.async { (_, cb) =>
      register {
        case Left(t)  => cb(Failure(t))
        case Right(t) => cb(Success(t))
      }

      Cancelable.empty
    }

  override def error[T](t: Throwable): Task[T] = Task.raiseError(t)
}
