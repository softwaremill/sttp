package sttp.client4

import sttp.client4.httpclient.HttpClientFutureBackend
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.websocket.{WebSocketConcurrentTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.Future

class HttpClientFutureWebSocketTest[F[_]] extends WebSocketTest[Future] with WebSocketConcurrentTest[Future] {
  override val backend: WebSocketBackend[Future] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def concurrently[T](fs: List[() => Future[T]]): Future[List[T]] = Future.sequence(fs.map(_()))
}
