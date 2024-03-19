package sttp.client3.asynchttpclient.zio

import sttp.client3._
import sttp.client3.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import zio.Task

class AsyncHttpClientZioHttpTest extends AsyncHttpClientHttpTest[Task] with ZioTestBase {

  override val backend: SttpBackend[Task, Any] =
    runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

}
