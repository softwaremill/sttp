package sttp.client4.httpclient.zio

import sttp.client4._
import sttp.client4.impl.zio.ZioTestBase
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import zio.Task

class HttpClientZioHttpTest extends HttpTest[Task] with ZioTestBase {
  override val backend: Backend[Task] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def supportsHostHeaderOverride = false

  "compile" - {
    "SttpClient usage" in {
      val request = basicRequest.post(uri"http://example.com").body("hello")
      send(request).provideLayer(HttpClientZioBackend.layer())
      succeed
    }
  }
}
