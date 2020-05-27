package sttp.client.asynchttpclient.monix

import java.util.concurrent.TimeoutException

import monix.eval.Task
import sttp.client._
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{CancelTest, ConvertToFuture, HttpTest}
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.duration._

class AsyncHttpClientMonixHttpTest extends HttpTest[Task] with CancelTest[Task, Nothing] {
  override implicit val backend: SttpBackend[Task, Nothing, NothingT] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover {
        case _: TimeoutException => None
      }

  override def throwsExceptionOnUnsupportedEncoding = false
}
