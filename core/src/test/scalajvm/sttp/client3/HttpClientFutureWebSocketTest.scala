package sttp.client3

import sttp.capabilities.WebSockets
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketConcurrentTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future

class HttpClientFutureWebSocketTest[F[_]] extends WebSocketTest[Future] with WebSocketConcurrentTest[Future] {
  override val backend: SttpBackend[Future, WebSockets] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def concurrently[T](fs: List[() => Future[T]]): Future[List[T]] = Future.sequence(fs.map(_()))
}
