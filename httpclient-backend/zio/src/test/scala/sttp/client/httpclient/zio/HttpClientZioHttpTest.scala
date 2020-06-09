package sttp.client.httpclient.zio

import sttp.client.httpclient.zio.HttpClientZioBackend.BlockingTask
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.zio._
import sttp.client.testing.{ConvertToFuture, HttpTest}

class HttpClientZioHttpTest extends HttpTest[BlockingTask]{
  override implicit val backend: SttpBackend[BlockingTask, Nothing, NothingT] = runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture
}
