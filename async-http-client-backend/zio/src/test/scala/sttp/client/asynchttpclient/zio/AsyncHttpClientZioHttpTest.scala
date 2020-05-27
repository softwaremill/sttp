package sttp.client.asynchttpclient.zio

import sttp.client._
import sttp.client.impl.zio._
import sttp.client.testing.{CancelTest, ConvertToFuture, HttpTest}
import zio.Task
import zio.clock.Clock
import zio.duration._

class AsyncHttpClientZioHttpTest extends HttpTest[Task] with CancelTest[Task, Nothing] {

  override implicit val backend: SttpBackend[Task, Nothing, NothingT] = runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] =
    t.timeout(timeoutMillis.milliseconds).provideLayer(Clock.live)

  override def throwsExceptionOnUnsupportedEncoding = false
}
