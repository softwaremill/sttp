package sttp.client4.curl.zio

import zio.Task
import sttp.client4.testing.HttpTest
import sttp.client4.Backend
import sttp.client4.impl.zio.ZioTestBase
import sttp.client4.testing.ConvertToFuture

class CurlZioHttpTest extends HttpTest[Task] with ZioTestBase {
  val backend: Backend[Task] = CurlZioBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsDeflateWrapperChecking = false
  override def supportsCancellation = false
}
