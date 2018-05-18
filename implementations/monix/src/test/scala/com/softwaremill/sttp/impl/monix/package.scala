package com.softwaremill.sttp.impl

import scala.concurrent.Future

import _root_.monix.eval.Task
import com.softwaremill.sttp.testing.streaming.ConvertToFuture

package object monix {

  val convertMonixTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    import _root_.monix.execution.Scheduler.Implicits.global

    override def toFuture[T](value: Task[T]): Future[T] = value.runAsync
  }
}
