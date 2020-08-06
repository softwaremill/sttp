package sttp.client.okhttp

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, ConcurrentLinkedQueue, LinkedBlockingQueue}

import sttp.client.ws.internal.AsyncQueue
import sttp.model.ws.WebSocketBufferFull

import scala.concurrent.{Future, blocking}
import scala.concurrent.ExecutionContext.Implicits.global //TODO ??? or should we call blocking {}?

class FutureAsyncQueue[T](capacity: Option[Int]) extends AsyncQueue[Future, T] {

  private val queue: BlockingQueue[T] = capacity match {
    case Some(value) => new ArrayBlockingQueue[T](value)
    case None        => new LinkedBlockingQueue[T]()
  }

  /**
    * Eagerly adds the given item to the queue.
    */
  override def offer(t: T): Unit = {
    if (!queue.offer(t)) {
      throw new WebSocketBufferFull()
    }
  }

  /**
    * Takes an element from the queue or suspends, until one is available. May be eager or lazy, depending on `F`.
    */
  override def poll: Future[T] = Future(blocking(queue.poll()))
}
