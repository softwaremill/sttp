package sttp.client3.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client3.SttpBackend
import sttp.client3.impl.monix.convertMonixTaskToFuture
import sttp.client3.okhttp.OkHttpHttpTest
import sttp.client3.testing.ConvertToFuture

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

class OkHttpMonixHttpTest extends OkHttpHttpTest[Task] {

  override val backend: SttpBackend[Task, MonixStreams] = OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override def supportsDeflateWrapperChecking = false

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.map(Some(_))
      .timeout(timeoutMillis.milliseconds)
      .onErrorRecover { case _: TimeoutException =>
        None
      }
}
