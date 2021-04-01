package sttp.client3.httpclient.monix

import cats.effect.Concurrent
import cats.effect.concurrent.Semaphore
import monix.eval.Task
import sttp.client3.httpclient.Sequencer

private[monix] class MonixSequencer(s: Semaphore[Task]) extends Sequencer[Task] {
  override def apply[T](t: => Task[T]): Task[T] = s.withPermit(t)
}

private[monix] object MonixSequencer {
  def create(implicit c: Concurrent[Task]): Task[MonixSequencer] = Semaphore(1).map(new MonixSequencer(_))
}
