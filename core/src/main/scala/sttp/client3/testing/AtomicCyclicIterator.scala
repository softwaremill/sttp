package sttp.client3.testing

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator
import scala.util.{Failure, Success, Try}

final class AtomicCyclicIterator[+T] private(val elements: Seq[T]) {
  private val vector = elements.toVector
  private val lastIndex = elements.length - 1
  private val currentIndex = new AtomicInteger(0)

  private val toNextIndex = new IntUnaryOperator {
    final override def applyAsInt(i: Int): Int =
      if (i == lastIndex) 0 else i + 1
  }

  def next(): T = {
    val index = currentIndex.getAndUpdate(toNextIndex)
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
