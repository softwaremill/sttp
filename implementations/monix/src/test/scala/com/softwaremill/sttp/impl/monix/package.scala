package com.softwaremill.sttp.impl

import scala.concurrent.Future

import _root_.monix.eval.Task
import com.softwaremill.sttp.testing.ConvertToFuture

package object monix {

  val convertToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    import _root_.monix.execution.Scheduler.Implicits.global

    override def toFuture[T](value: Task[T]): Future[T] = value.runAsync
  }
}
