package sttp.client.asynchttpclient.future

import sttp.client.SttpBackend
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends HttpTest[Future] {

  override val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def throwsExceptionOnUnsupportedEncoding = false
}
