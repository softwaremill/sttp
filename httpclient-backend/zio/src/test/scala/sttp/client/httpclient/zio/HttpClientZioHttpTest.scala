package sttp.client.httpclient.zio

import sttp.client._
import sttp.client.impl.zio.ZioTestBase
import sttp.client.testing.{ConvertToFuture, HttpTest}

class HttpClientZioHttpTest extends HttpTest[BlockingTask] with ZioTestBase {
  override val backend: SttpBackend[BlockingTask, Any] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture

  "compile" - {
    "SttpClient usage" in {
      import _root_.zio.blocking._
      val request = basicRequest.post(uri"http://example.com").body("hello")
      SttpClient.send(request).provideLayer(Blocking.live >+> HttpClientZioBackend.layer())
      succeed
    }
  }
}
