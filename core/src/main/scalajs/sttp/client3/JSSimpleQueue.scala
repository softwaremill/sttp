package sttp.client3

import sttp.client3.internal.ConvertFromFuture
import sttp.client3.internal.ws.SimpleQueue

import scala.concurrent.{Future, Promise}

private[client3] class JSSimpleQueue[F[_], T](implicit fromFuture: ConvertFromFuture[F]) extends SimpleQueue[F, T] {

  var state: Either[List[Promise[T]], List[T]] = Right(List())

  def offer(t: T): Unit = state match {
    case Left(promises) =>
      promises.headOption match {
        case Some(p) =>
          p.success(t)
          state = Left(promises.tail)
        case None => state = Right(List(t))
      }
    case Right(elems) => state = Right(elems :+ t)
  }

  def poll: F[T] = fromFuture {
    state match {
      case Right(elems) =>
        elems.headOption match {
          case Some(t) =>
            state = Right(elems.tail)
            Future.successful(t)
          case None =>
            val p = Promise[T]()
            state = Left(List(p))
            p.future
        }
      case Left(promises) =>
        val p = Promise[T]()
        state = Left(promises :+ p)
        p.future
    }
  }
}
