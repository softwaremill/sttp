package sttp.client3.akkahttp

import sttp.client3.SttpBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AkkaHttpClientHttpTest extends HttpTest[Future] {
  override val backend: SttpBackend[Future, Any] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
