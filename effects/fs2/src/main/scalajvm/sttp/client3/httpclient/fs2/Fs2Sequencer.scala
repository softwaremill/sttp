package sttp.client3.httpclient.fs2

import cats.syntax.all._
import cats.effect.{Concurrent, MonadCancel}
import cats.effect.std.Semaphore
import sttp.client3.internal.httpclient.Sequencer

private[fs2] class Fs2Sequencer[F[_]](s: Semaphore[F])(implicit m: MonadCancel[F, Throwable]) extends Sequencer[F] {
  override def apply[T](t: => F[T]): F[T] = s.permit.use(_ => t)
}

private[fs2] object Fs2Sequencer {
  def create[F[_]: Concurrent]: F[Sequencer[F]] = Semaphore(1).map(new Fs2Sequencer(_))
}
