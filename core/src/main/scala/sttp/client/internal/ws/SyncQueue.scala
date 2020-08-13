package sttp.client.internal.ws

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, LinkedBlockingQueue}

import sttp.client.Identity
import sttp.ws.WebSocketBufferFull

class SyncQueue[T](capacity: Option[Int]) extends SimpleQueue[Identity, T] {

  private val queue: BlockingQueue[T] = capacity match {
    case Some(value) => new ArrayBlockingQueue[T](value)
    case None        => new LinkedBlockingQueue[T]()
  }

  /**
    * Eagerly adds the given item to the queue.
    */
  override def offer(t: T): Unit = {
    if (!queue.offer(t)) {
      throw new WebSocketBufferFull(capacity.getOrElse(Int.MaxValue))
    }
  }

  /**
    * Takes an element from the queue or suspends, until one is available. May be eager or lazy, depending on `F`.
    */
  override def poll: Identity[T] = queue.take()
}
