package com.softwaremill.sttp.testing

import org.scalatest.Suite
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds

trait ToFutureWrapper extends ScalaFutures with TestingPatience { this: Suite =>

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

trait TestingPatience extends PatienceConfiguration {
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 250.milliseconds)
}
