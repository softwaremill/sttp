package sttp.client3.testing

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator
import scala.util.{Failure, Success, Try}

final class AtomicCyclicIterator[+A] private(val elements: Seq[A]) {
  private val vector = elements.toVector
  private val lastIndex = elements.length - 1
  private val currentIndex = new AtomicInteger(0)

  private val toNextIndex = new IntUnaryOperator {
    final override def applyAsInt(i: Int): Int =
      if (i == lastIndex) 0 else i + 1
  }

  def next(): A = {
    val index = currentIndex.getAndUpdate(toNextIndex)
    vector(index)
  }
}

object AtomicCyclicIterator {

  def tryFrom[A](elements: Seq[A]): Try[AtomicCyclicIterator[A]] = {
    if (elements.nonEmpty)
      Success(new AtomicCyclicIterator(elements))
    else
      Failure(new IllegalArgumentException("Argument must be a non-empty collection."))
  }

  def unsafeFrom[A](elements: Seq[A]): AtomicCyclicIterator[A] = tryFrom(elements).get

  def apply[A](element1: A, rest: Seq[A]): AtomicCyclicIterator[A] = unsafeFrom(element1 +: rest)

  def of[A](element1: A, rest: A*): AtomicCyclicIterator[A] = apply(element1, rest)
}
