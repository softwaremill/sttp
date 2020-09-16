package sttp.client3.impl.monix

import monix.eval.Task
import sttp.monad.{Canceler, MonadAsyncError}

import scala.util.{Failure, Success}

object TaskMonadAsyncError extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.now(t)

  override def map[T, T2](fa: Task[T])(f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T])(f: (T) => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](register: (Either[Throwable, T] => Unit) => Canceler): Task[T] =
    Task.cancelable { cb =>
      val canceler = register {
        case Left(t)  => cb(Failure(t))
        case Right(t) => cb(Success(t))
      }
      Task(canceler.cancel())
    }

  override def error[T](t: Throwable): Task[T] = Task.raiseError(t)

  override protected def handleWrappedError[T](rt: Task[T])(h: PartialFunction[Throwable, Task[T]]): Task[T] =
    rt.onErrorRecoverWith(h)

  override def eval[T](t: => T): Task[T] = Task(t)

  override def suspend[T](t: => Task[T]): Task[T] = Task.suspend(t)

  override def flatten[T](ffa: Task[Task[T]]): Task[T] = ffa.flatten

  override def ensure[T](f: Task[T], e: => Task[Unit]): Task[T] = f.guarantee(e)
}
