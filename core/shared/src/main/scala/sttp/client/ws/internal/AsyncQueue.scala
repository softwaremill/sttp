package sttp.client.ws.internal

import scala.language.higherKinds

trait AsyncQueue[F[_], T] {

  /**
    * Eagerly clears the queue.
    */
  def clear(): Unit

  /**
    * Eagerly adds the given item to the queue.
    */
  def offer(t: T): Unit

  /**
    * Takes an element from the queue or suspends, until one is available. May be eager or lazy, depending on `F`.
    */
  def poll: F[T]
}
