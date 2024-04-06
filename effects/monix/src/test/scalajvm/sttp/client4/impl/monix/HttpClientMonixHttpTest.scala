package sttp.client4.impl.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client4.Backend
import sttp.client4.httpclient.monix.HttpClientMonixBackend
import sttp.client4.testing.{ConvertToFuture, HttpTest}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

class HttpClientMonixHttpTest extends HttpTest[Task] {
  override val backend: Backend[Task] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsDeflateWrapperChecking = false
  override def supportsResponseAsInputStream = false

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }
}
