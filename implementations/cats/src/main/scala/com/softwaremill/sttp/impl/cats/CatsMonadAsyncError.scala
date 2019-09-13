package com.softwaremill.sttp.impl.cats

import cats.effect.Async
import com.softwaremill.sttp.monad.MonadAsyncError

import scala.language.higherKinds

class CatsMonadAsyncError[F[_]](implicit F: Async[F]) extends CatsMonadError[F] with MonadAsyncError[F] {
  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): F[T] =
    F.async(register)
}
