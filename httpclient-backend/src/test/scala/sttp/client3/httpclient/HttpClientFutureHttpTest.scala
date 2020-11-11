package sttp.client3.httpclient

import sttp.client3.testing.{ConvertToFuture, HttpTest}
import sttp.client3.SttpBackend

import scala.concurrent.Future

class HttpClientFutureHttpTest extends HttpTest[Future] {
  override val backend: SttpBackend[Future, Any] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
}
