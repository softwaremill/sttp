package sttp.client3.httpclient.zio

import sttp.client3._
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import zio.Task

class HttpClientZioHttpTest extends HttpTest[Task] with ZioTestBase {
  override val backend: SttpBackend[Task, Any] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  "compile" - {
    "SttpClient usage" in {
      import _root_.zio.blocking._
      val request = basicRequest.post(uri"http://example.com").body("hello")
      send(request).provideLayer(Blocking.live >+> HttpClientZioBackend.layer())
      succeed
    }
  }
}
