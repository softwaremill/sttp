package sttp.client3.testing

import scala.concurrent.Future

import org.scalatest.exceptions.TestFailedException

trait ToFutureWrapper {

  implicit final class ConvertToFutureDecorator[F[_], T](wrapped: => F[T]) {
    def toFuture()(implicit ctf: ConvertToFuture[F]): Future[T] = {
      try {
        ctf.toFuture(wrapped)
      } catch {
        case e: TestFailedException if e.getCause != null => Future.failed(e.getCause)
      }
    }
  }
}
