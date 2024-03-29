package sttp.client4.impl.scalaz

import scalaz.concurrent.Task
import scalaz.{-\/, \/-}
import sttp.monad.{Canceler, MonadAsyncError}

object TaskMonadAsyncError extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.point(t)

  override def map[T, T2](fa: Task[T])(f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T])(f: (T) => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](register: (Either[Throwable, T] => Unit) => Canceler): Task[T] =
    Task.async { cb =>
      register {
        case Left(t)  => cb(-\/(t))
        case Right(t) => cb(\/-(t))
      }
    }

  override def error[T](t: Throwable): Task[T] = Task.fail(t)

  override protected def handleWrappedError[T](rt: Task[T])(h: PartialFunction[Throwable, Task[T]]): Task[T] =
    rt.handleWith(h)

  override def eval[T](t: => T): Task[T] = Task(t)

  override def ensure[T](f: Task[T], e: => Task[Unit]): Task[T] = f.onFinish(_ => e)
}
