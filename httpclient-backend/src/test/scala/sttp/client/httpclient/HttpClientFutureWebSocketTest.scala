package sttp.client.httpclient

import sttp.capabilities.WebSockets
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.WebSocketTest
import sttp.client.SttpBackend
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future

class HttpClientFutureWebSocketTest[F[_]] extends WebSocketTest[Future] {
  override val backend: SttpBackend[Future, WebSockets] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()
}
