package sttp.client3.asynchttpclient.monix

import java.util.concurrent.TimeoutException

import monix.eval.Task
import sttp.client3._
import sttp.client3.impl.monix.convertMonixTaskToFuture
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.duration._

class AsyncHttpClientMonixHttpTest extends HttpTest[Task] {
  override val backend: SttpBackend[Task, Any] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }

  override def throwsExceptionOnUnsupportedEncoding = false
  override def supportsAutoDecompressionDisabling = false
}
