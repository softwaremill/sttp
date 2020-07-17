package sttp.client.httpclient.zio

import sttp.client.httpclient.zio.BlockingTask
import sttp.client._
import sttp.client.impl.zio._
import sttp.client.testing.{ConvertToFuture, HttpTest}

class HttpClientZioHttpTest extends HttpTest[BlockingTask] {
  override implicit val backend: SttpBackend[BlockingTask, Nothing, NothingT] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture

  "compile" - {
    "SttpClient usage" in {
      import zio.blocking._
      val request = basicRequest.post(uri"http://example.com").body("hello")
      SttpClient.send(request).provideSomeLayer(HttpClientZioBackend.layer()).provideLayer(Blocking.live)
      succeed
    }
  }
}
