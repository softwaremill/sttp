package sttp.client.httpclient.zio

import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.zio._
import sttp.client.testing.{ConvertToFuture, HttpTest}
import zio._

class HttpClientZioHttpTest extends HttpTest[Task]{
  override implicit val backend: SttpBackend[Task, Nothing, NothingT] = runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
}
