package sttp.client3.impl.cats

import cats.effect.kernel.Async
import cats.syntax.functor._
import cats.syntax.option._
import sttp.monad.{Canceler, MonadAsyncError}

class CatsMonadAsyncError[F[_]](implicit F: Async[F]) extends CatsMonadError[F] with MonadAsyncError[F] {
  override def async[T](register: ((Either[Throwable, T]) => Unit) => Canceler): F[T] =
    F.async(cb => F.delay(register(cb)).map(c => F.delay(c.cancel()).some))
}
