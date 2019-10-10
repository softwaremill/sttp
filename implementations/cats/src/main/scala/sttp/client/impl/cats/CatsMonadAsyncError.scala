package sttp.client.impl.cats

import cats.effect.Async
import sttp.client.monad.MonadAsyncError

import scala.language.higherKinds

class CatsMonadAsyncError[F[_]](implicit F: Async[F]) extends CatsMonadError[F] with MonadAsyncError[F] {
  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): F[T] = F.async(register)

  override def eval[T](t: => T): F[T] = F.delay(t)
}
