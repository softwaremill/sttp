package sttp.client.impl.cats

import sttp.client.monad.MonadError

import scala.language.higherKinds

class CatsMonadError[F[_]](implicit F: cats.MonadError[F, Throwable]) extends MonadError[F] {

  override def unit[T](t: T): F[T] = F.pure(t)

  override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)

  override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] =
    F.flatMap(fa)(f)

  override def error[T](t: Throwable): F[T] = F.raiseError(t)

  override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
    F.recoverWith(rt)(h)
}
