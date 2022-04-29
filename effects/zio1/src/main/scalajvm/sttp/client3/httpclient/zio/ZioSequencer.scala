package sttp.client3.httpclient.zio

import sttp.client3.internal.httpclient.Sequencer
import zio.{Semaphore, Task, UIO}

private[zio] class ZioSequencer(s: Semaphore) extends Sequencer[Task] {
  override def apply[T](t: => Task[T]): Task[T] = s.withPermit(t)
}

private[zio] object ZioSequencer {
  def create: UIO[ZioSequencer] = Semaphore.make(1).map(new ZioSequencer(_))
}
