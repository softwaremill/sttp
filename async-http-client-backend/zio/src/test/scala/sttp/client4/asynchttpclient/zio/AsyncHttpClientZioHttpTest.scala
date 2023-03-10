package sttp.client4.asynchttpclient.zio

import sttp.client4._
import sttp.client4.impl.zio.ZioTestBase
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import zio.Task

class AsyncHttpClientZioHttpTest extends HttpTest[Task] with ZioTestBase {

  override val backend: Backend[Task] =
    unsafeRunSyncOrThrow(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def throwsExceptionOnUnsupportedEncoding = false
  override def supportsAutoDecompressionDisabling = false

}
