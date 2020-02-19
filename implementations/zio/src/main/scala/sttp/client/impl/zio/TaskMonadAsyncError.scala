package sttp.client.impl.zio

import sttp.client.monad.{Canceler, MonadAsyncError}
import zio.{Task, UIO}

object TaskMonadAsyncError extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.succeed(t)

  override def map[T, T2](fa: Task[T])(f: T => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T])(f: T => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](register: (Either[Throwable, T] => Unit) => Canceler): Task[T] =
    Task.effectAsyncInterrupt { cb =>
      val canceler = register {
        case Left(t)  => cb(Task.fail(t))
        case Right(t) => cb(Task.succeed(t))
      }

      Left(UIO(canceler.cancel()))
    }

  override def error[T](t: Throwable): Task[T] = Task.fail(t)

  override protected def handleWrappedError[T](rt: Task[T])(h: PartialFunction[Throwable, Task[T]]): Task[T] =
    rt.catchSome(h)

  override def eval[T](t: => T): Task[T] = Task(t)
}
