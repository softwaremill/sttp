package sttp.client3.asynchttpclient.future

import sttp.client3.SttpBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends HttpTest[Future] {

  override val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def throwsExceptionOnUnsupportedEncoding = false
}
