package sttp.client.asynchttpclient.future

import sttp.client.{NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def throwsExceptionOnUnsupportedEncoding = false
}
