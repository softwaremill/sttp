package sttp.client3.testing

import java.util.concurrent.atomic.AtomicInteger
import scala.util.{Failure, Success, Try}

final class AtomicCyclicIterator[+T] private (val elements: Seq[T]) {
  private val vector = elements.toVector
  private val length = elements.length
  private val currentIndex = new AtomicInteger(0)

  def next(): T = {
    val index = currentIndex.getAndIncrement % length
    vector(index)
  }
}

object AtomicCyclicIterator {

  def tryFrom[T](elements: Seq[T]): Try[AtomicCyclicIterator[T]] = {
    if (elements.nonEmpty)
      Success(new AtomicCyclicIterator(elements))
    else
      Failure(new IllegalArgumentException("Argument must be a non-empty collection."))
  }

  def unsafeFrom[T](elements: Seq[T]): AtomicCyclicIterator[T] = tryFrom(elements).get

  def apply[T](head: T, tail: Seq[T]): AtomicCyclicIterator[T] = unsafeFrom(head +: tail)

  def of[T](head: T, tail: T*): AtomicCyclicIterator[T] = apply(head, tail)
}
