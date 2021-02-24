package sttp.client3.impl.monix

import monix.eval.Task
import monix.execution.{Scheduler, AsyncQueue => MAsyncQueue}
import sttp.client3.internal.ws.SimpleQueue
import sttp.ws.WebSocketBufferFull

class MonixSimpleQueue[A](bufferCapacity: Option[Int])(implicit s: Scheduler) extends SimpleQueue[Task, A] {
  private val queue = bufferCapacity match {
    case Some(capacity) => MAsyncQueue.bounded[A](capacity)
    case None           => MAsyncQueue.unbounded[A]()
  }

  override def offer(t: A): Unit = {
    if (!queue.tryOffer(t)) {
      throw WebSocketBufferFull(bufferCapacity.getOrElse(Int.MaxValue))
    }
  }
  override def poll: Task[A] = Task.deferFuture(queue.poll())
}
