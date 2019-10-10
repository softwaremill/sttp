package sttp.client.akkahttp

import sttp.client.{NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AkkaHttpClientHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing, NothingT] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
