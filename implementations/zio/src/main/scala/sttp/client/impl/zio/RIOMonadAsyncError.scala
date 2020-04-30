package sttp.client.impl.zio

import sttp.client.monad.{Canceler, MonadAsyncError}
import zio.{RIO, UIO}

class RIOMonadAsyncError[R] extends MonadAsyncError[RIO[R, *]] {
  override def unit[T](t: T): RIO[R, T] = RIO.succeed(t)

  override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)

  override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] =
    fa.flatMap(f)

  override def async[T](register: (Either[Throwable, T] => Unit) => Canceler): RIO[R, T] =
    RIO.effectAsyncInterrupt { cb =>
      val canceler = register {
        case Left(t)  => cb(RIO.fail(t))
        case Right(t) => cb(RIO.succeed(t))
      }

      Left(UIO(canceler.cancel()))
    }

  override def error[T](t: Throwable): RIO[R, T] = RIO.fail(t)

  override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] =
    rt.catchSome(h)

  override def eval[T](t: => T): RIO[R, T] = RIO.effect(t)
}
