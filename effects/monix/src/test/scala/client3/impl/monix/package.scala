package client3.impl

import scala.concurrent.Future

import _root_.monix.eval.Task
import sttp.client3.testing.ConvertToFuture

package object monix {

  val convertMonixTaskToFuture: ConvertToFuture[Task] = new ConvertToFuture[Task] {
    import _root_.monix.execution.Scheduler.Implicits.global

    override def toFuture[T](value: Task[T]): Future[T] = value.runToFuture
  }
}
