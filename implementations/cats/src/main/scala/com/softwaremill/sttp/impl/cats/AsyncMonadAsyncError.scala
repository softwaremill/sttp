package com.softwaremill.sttp.impl.cats

import cats.effect.{Async, Effect}
import com.softwaremill.sttp.MonadAsyncError

import scala.language.higherKinds

class AsyncMonadAsyncError[F[_]](implicit F: Async[F]) extends MonadAsyncError[F] {

  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): F[T] =
    F.async(register)

  override def unit[T](t: T): F[T] = F.pure(t)

  override def map[T, T2](fa: F[T])(f: (T) => T2): F[T2] = F.map(fa)(f)

  override def flatMap[T, T2](fa: F[T])(f: (T) => F[T2]): F[T2] =
    F.flatMap(fa)(f)

  override def error[T](t: Throwable): F[T] = F.raiseError(t)

  override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
    F.recoverWith(rt)(h)
}

class EffectMonadAsyncError[F[_]](implicit F: Effect[F]) extends AsyncMonadAsyncError[F]
