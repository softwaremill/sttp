package sttp.client4.armeria.monix

import java.util.concurrent.TimeoutException
import monix.eval.Task
import sttp.client4.Backend
import sttp.client4.impl.monix.convertMonixTaskToFuture
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.duration.DurationInt

class ArmeriaMonixHttpTest extends HttpTest[Task] {
  override val backend: Backend[Task] = ArmeriaMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }

  override def supportsHostHeaderOverride = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
  override def supportsEmptyContentEncoding = false
  override def supportsResponseAsInputStream = false
}
