package sttp.client.akkahttp

import sttp.client.SttpBackend
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AkkaHttpClientHttpTest extends HttpTest[Future] {
  override implicit val backend: SttpBackend[Future, Any] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
