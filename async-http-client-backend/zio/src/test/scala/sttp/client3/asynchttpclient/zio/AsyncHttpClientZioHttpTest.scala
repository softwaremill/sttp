package sttp.client3.asynchttpclient.zio

import sttp.client3._
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.{CancelTest, ConvertToFuture, HttpTest}
import zio.Task
import zio.clock.Clock
import zio.duration._

class AsyncHttpClientZioHttpTest extends HttpTest[Task] with CancelTest[Task, Any] with ZioTestBase {

  override val backend: SttpBackend[Task, Any] =
    runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.timeout(timeoutMillis.milliseconds).provideLayer(Clock.live)

  override def throwsExceptionOnUnsupportedEncoding = false

}
