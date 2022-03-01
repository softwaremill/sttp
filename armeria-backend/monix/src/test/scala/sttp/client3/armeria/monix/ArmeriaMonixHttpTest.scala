package sttp.client3.armeria.monix

import java.util.concurrent.TimeoutException
import monix.eval.Task
import sttp.client3.SttpBackend
import sttp.client3.impl.monix.convertMonixTaskToFuture
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.duration.DurationInt

class ArmeriaMonixHttpTest extends HttpTest[Task] {
  override val backend: SttpBackend[Task, Any] = ArmeriaMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }

  override def supportsHostHeaderOverride = false
  override def supportsAutoDecompressionDisabling = false

}
