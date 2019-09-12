package com.softwaremill.sttp.impl.cats

import cats.effect.{Async, Effect}
import com.softwaremill.sttp.MonadAsyncError

import scala.language.higherKinds

class CatsMonadAsyncError[F[_]](implicit F: Async[F]) extends CatsMonadError[F] with MonadAsyncError[F] {
  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): F[T] =
    F.async(register)
}
