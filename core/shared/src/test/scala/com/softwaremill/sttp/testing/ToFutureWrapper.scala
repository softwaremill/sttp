package com.softwaremill.sttp.testing

import scala.concurrent.Future
import scala.language.higherKinds

import org.scalatest.exceptions.TestFailedException

trait ToFutureWrapper {

  implicit final class ConvertToFutureDecorator[R[_], T](wrapped: => R[T]) {
    def toFuture()(implicit ctf: ConvertToFuture[R]): Future[T] = {
      try {
        ctf.toFuture(wrapped)
      } catch {
        case e: TestFailedException if e.getCause != null => Future.failed(e.getCause)
      }
    }
  }
}
