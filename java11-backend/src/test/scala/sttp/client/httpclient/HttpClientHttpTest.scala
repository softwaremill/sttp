package sttp.client.httpclient

import java.net.http.HttpClient

import sttp.client.monad.FutureMonad
import sttp.client.{HttpURLConnectionBackend, NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class HttpClientHttpTest extends HttpTest[Future] {
  override implicit val backend: SttpBackend[Future, Nothing, NothingT] =
    new HttpClientBackend[Future, Nothing](HttpClient.newHttpClient(), new FutureMonad())
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
