package sttp.client3.httpclient.cats

import cats.effect.kernel.{Async, MonadCancel}
import cats.effect.std.Semaphore
import cats.syntax.all._
import sttp.client3.internal.httpclient.Sequencer

private[cats] class CatsSequencer[F[_]](s: Semaphore[F])(implicit m: MonadCancel[F, Throwable]) extends Sequencer[F] {
  override def apply[T](t: => F[T]): F[T] = s.permit.use(_ => t)
}

private[cats] object CatsSequencer {
  def create[F[_]: Async]: F[Sequencer[F]] = Semaphore(1).map(new CatsSequencer(_))
}
