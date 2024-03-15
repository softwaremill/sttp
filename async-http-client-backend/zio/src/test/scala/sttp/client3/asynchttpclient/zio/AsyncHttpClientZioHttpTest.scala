package sttp.client3.asynchttpclient.zio

import sttp.client3._
import sttp.client3.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.ConvertToFuture
import zio.Task

class AsyncHttpClientZioHttpTest extends AsyncHttpClientHttpTest[Task] with ZioTestBase {

  override val backend: SttpBackend[Task, Any] =
    unsafeRunSyncOrThrow(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def throwsExceptionOnUnsupportedEncoding = false
  override def supportsAutoDecompressionDisabling = false

}
