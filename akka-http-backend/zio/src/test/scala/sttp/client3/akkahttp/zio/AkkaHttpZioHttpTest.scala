package sttp.client3.akkahttp.zio

import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import zio.Task

class AkkaHttpZioHttpTest extends HttpTest[Task] with ZioTestBase {
  val underlying = AkkaHttpBackend()

  override val backend: SttpBackend[Task, Any] =
    runtime.unsafeRun(AkkaHttpClientZioBackend(underlying))
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def throwsExceptionOnUnsupportedEncoding = false

}
