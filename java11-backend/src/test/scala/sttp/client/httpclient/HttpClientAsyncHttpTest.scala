package sttp.client.httpclient

import java.net.http.HttpClient

import sttp.client.monad.FutureMonad
import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class HttpClientAsyncHttpTest extends HttpTest[Future] {
  override implicit val backend: SttpBackend[Future, Nothing, NothingT] =
    new HttpClientAsyncBackend[Future, Nothing](HttpClient.newHttpClient(), new FutureMonad())
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
