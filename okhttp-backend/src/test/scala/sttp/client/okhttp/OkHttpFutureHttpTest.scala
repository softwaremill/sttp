package sttp.client.okhttp

import sttp.client.{NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class OkHttpFutureHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing, NothingT] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
