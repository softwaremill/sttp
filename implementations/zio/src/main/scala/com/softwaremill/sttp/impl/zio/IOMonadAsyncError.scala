package com.softwaremill.sttp.impl.zio

import com.softwaremill.sttp.MonadAsyncError
import zio.{IO, ZIO}

object IOMonadAsyncError extends MonadAsyncError[IO[Throwable, ?]] {
  override def unit[T](t: T): IO[Throwable, T] = IO.succeed(t)

  override def map[T, T2](fa: IO[Throwable, T])(f: T => T2): IO[Throwable, T2] = fa.map(f)

  override def flatMap[T, T2](fa: IO[Throwable, T])(f: T => IO[Throwable, T2]): IO[Throwable, T2] =
    fa.flatMap(f)

  override def async[T](register: (Either[Throwable, T] => Unit) => Unit): IO[Throwable, T] =
    ZIO.effectAsync { cb =>
      register {
        case Left(t)  => cb(IO.fail(t))
        case Right(t) => cb(IO.succeed(t))
      }
    }

  override def error[T](t: Throwable): IO[Throwable, T] = IO.fail(t)

  override protected def handleWrappedError[T](
      rt: IO[Throwable, T]
  )(h: PartialFunction[Throwable, IO[Throwable, T]]): IO[Throwable, T] =
    rt.catchSome(h)
}
