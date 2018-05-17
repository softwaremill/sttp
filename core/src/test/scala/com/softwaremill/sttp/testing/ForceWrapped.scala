package com.softwaremill.sttp.testing

import org.scalatest.Suite
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds

trait ForceWrapped extends ScalaFutures with TestingPatience { this: Suite =>

  implicit class ForceDecorator[R[_], T](wrapped: R[T]) {
    def toFuture()(implicit ctf: ConvertToFuture[R]): Future[T] =
      ctf.toFuture(wrapped)

    def force()(implicit ctf: ConvertToFuture[R]): T = {
      try {
        ctf.toFuture(wrapped).futureValue
      } catch {
        case e: TestFailedException if e.getCause != null => throw e.getCause
      }
    }
  }
}

trait TestingPatience extends PatienceConfiguration {
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 150.milliseconds)
}
