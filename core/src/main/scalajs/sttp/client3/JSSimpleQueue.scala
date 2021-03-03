package sttp.client3

import sttp.client3.internal.ConvertFromFuture
import sttp.client3.internal.ws.SimpleQueue

import scala.concurrent.{Future, Promise}

private[client3] class JSSimpleQueue[F[_], T](implicit fromFuture: ConvertFromFuture[F]) extends SimpleQueue[F, T] {

  private var state: Either[List[Promise[T]], List[T]] = Right(List())

  def offer(t: T): Unit = state match {
    case Left(p :: promises) =>
      p.success(t)
      state = Left(promises)
    case Left(Nil)    => state = Right(List(t))
    case Right(elems) => state = Right(elems :+ t)
  }

  def poll: F[T] = fromFuture {
    state match {
      case Right(t :: elems) =>
        state = Right(elems)
        Future.successful(t)
      case Right(Nil) =>
        val p = Promise[T]()
        state = Left(List(p))
        p.future
      case Left(promises) =>
        val p = Promise[T]()
        state = Left(promises :+ p)
        p.future
    }
  }
}
