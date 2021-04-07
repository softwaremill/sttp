package sttp.client3.httpclient.fs2

import cats.syntax.all._
import cats.effect.Concurrent
import cats.effect.concurrent.Semaphore
import sttp.client3.httpclient.Sequencer

private[fs2] class Fs2Sequencer[F[_]](s: Semaphore[F]) extends Sequencer[F] {
  override def apply[T](t: => F[T]): F[T] = s.withPermit(t)
}

private[fs2] object Fs2Sequencer {
  def create[F[_]: Concurrent]: F[Sequencer[F]] = Semaphore(1).map(new Fs2Sequencer(_))
}
