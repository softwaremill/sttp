package sttp.client.asynchttpclient.future

import sttp.client.SttpBackend
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
