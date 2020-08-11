package sttp.client.okhttp

import sttp.client.{SttpBackend, WebSockets}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class OkHttpFutureHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, WebSockets] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
