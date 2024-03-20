package sttp.client4.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client4.impl.monix.convertMonixTaskToFuture
import sttp.client4.testing.ConvertToFuture

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

class AsyncHttpClientMonixHttpTest extends AsyncHttpClientHttpTest[Task] {
  override val backend: Backend[Task] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }
}
