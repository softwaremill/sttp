package sttp.client4.impl.cats

import cats.effect.Sync
import sttp.monad.MonadError

class CatsMonadError[F[_]](implicit F: Sync[F]) extends MonadError[F] {
  override def unit[T](t: T): F[T] = F.pure(t)

  override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)

  override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] =
    F.flatMap(fa)(f)

  override def error[T](t: Throwable): F[T] = F.raiseError(t)

  override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
    F.recoverWith(rt)(h)

  override def eval[T](t: => T): F[T] = F.delay(t)

  override def suspend[T](t: => F[T]): F[T] = F.defer(t)

  override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)

  override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guaranteeCase(f)(_ => e)
  override def ensure2[T](f: => F[T], e: => F[Unit]): F[T] = F.guaranteeCase(F.defer(f))(_ => e)
}
