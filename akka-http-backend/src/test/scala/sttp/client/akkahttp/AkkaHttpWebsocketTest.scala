package sttp.client.akkahttp

import sttp.client._
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.WebSocketTest

import scala.concurrent.{ExecutionContext, Future}

class AkkaHttpWebsocketTest extends WebSocketTest[Future] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override implicit val backend: SttpBackend[Future, WebSockets] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad
}
