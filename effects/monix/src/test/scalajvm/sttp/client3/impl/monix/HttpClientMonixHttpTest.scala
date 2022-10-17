package sttp.client3.impl.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client3.SttpBackend
import sttp.client3.httpclient.monix.HttpClientMonixBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

class HttpClientMonixHttpTest extends HttpTest[Task] {
  override val backend: SttpBackend[Task, Any] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsDeflateWrapperChecking = false

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }
}
