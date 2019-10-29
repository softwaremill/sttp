package sttp.client.httpclient

import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import sttp.client._
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.testing.{ConvertToFuture, TestHttpServer, ToFutureWrapper}

import scala.concurrent.Future

class HttpClientFutureWebsocketTest
    extends HttpClientWebsocketTest[Future]
    with Matchers
    with TestHttpServer
    with ToFutureWrapper
    with Eventually
    with IntegrationPatience {
  override implicit val backend: SttpBackend[Future, _, WebSocketHandler] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monadError: MonadError[Future] = new FutureMonad
}
