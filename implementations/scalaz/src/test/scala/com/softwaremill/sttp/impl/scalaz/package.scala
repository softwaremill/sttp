package com.softwaremill.sttp.impl

import com.softwaremill.sttp.testing.ConvertToFuture

import _root_.scalaz.concurrent.Task
import _root_.scalaz.{-\/, \/-}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

package object scalaz {

  val convertScalazTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    // from https://github.com/Verizon/delorean
    override def toFuture[T](value: Task[T]): Future[T] = {
      val p = Promise[T]()

      value.unsafePerformAsync {
        case \/-(a) => p.complete(Success(a)); ()
        case -\/(t) => p.complete(Failure(t)); ()
      }

      p.future
    }
  }
}
