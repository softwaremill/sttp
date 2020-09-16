package sttp.client3.okhttp

import sttp.capabilities.WebSockets
import sttp.client3.SttpBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class OkHttpFutureHttpTest extends HttpTest[Future] {

  override val backend: SttpBackend[Future, WebSockets] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
