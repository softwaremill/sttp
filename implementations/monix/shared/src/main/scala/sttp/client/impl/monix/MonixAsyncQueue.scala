package sttp.client.impl.monix

import monix.eval.Task
import sttp.model.ws.WebSocketBufferFull
import monix.execution.{Scheduler, AsyncQueue => MAsyncQueue}
import sttp.client.ws.internal.AsyncQueue

class MonixAsyncQueue[A](bufferCapacity: Option[Int])(implicit s: Scheduler) extends AsyncQueue[Task, A] {
  private val queue = bufferCapacity match {
    case Some(capacity) => MAsyncQueue.bounded[A](capacity)
    case None           => MAsyncQueue.unbounded[A]()
  }

  override def offer(t: A): Unit = {
    if (!queue.tryOffer(t)) {
      throw new WebSocketBufferFull()
    }
  }
  override def poll: Task[A] = Task.deferFuture(queue.poll())
}
