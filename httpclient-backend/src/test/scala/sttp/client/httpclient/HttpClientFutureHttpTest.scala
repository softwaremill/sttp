package sttp.client.httpclient

import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.SttpBackend

import scala.concurrent.Future

class HttpClientFutureHttpTest extends HttpTest[Future] {
  override val backend: SttpBackend[Future, Any] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
