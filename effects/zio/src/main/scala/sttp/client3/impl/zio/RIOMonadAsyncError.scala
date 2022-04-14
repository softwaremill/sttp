package sttp.client3.impl.zio

import sttp.monad.{Canceler, MonadAsyncError}
import zio.{RIO, UIO, ZIO}

class RIOMonadAsyncError[R] extends MonadAsyncError[RIO[R, *]] {
  override def unit[T](t: T): RIO[R, T] = RIO.succeed(t)

  override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)

  override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] =
    fa.flatMap(f)

  override def async[T](register: (Either[Throwable, T] => Unit) => Canceler): RIO[R, T] = {
    RIO.asyncInterrupt { cb =>
      val canceler = register {
        case Left(t)  => cb(RIO.fail(t))
        case Right(t) => cb(RIO.succeed(t))
      }

      Left(UIO.succeed(canceler.cancel()))
    }
  }

  override def error[T](t: Throwable): RIO[R, T] = RIO.fail(t)

  override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] =
    rt.catchSome(h)

  override def eval[T](t: => T): RIO[R, T] = RIO.succeed(t)

  override def suspend[T](t: => RIO[R, T]): RIO[R, T] = RIO.suspend(t)

  override def flatten[T](ffa: RIO[R, RIO[R, T]]): RIO[R, T] = ffa.flatten

  override def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.catchAll(_ => ZIO.unit))
}
