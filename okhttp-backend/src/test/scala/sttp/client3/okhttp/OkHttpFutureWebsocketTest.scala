package sttp.client3.okhttp

import sttp.capabilities.WebSockets
import sttp.client3._
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketBufferOverflowTest, WebSocketConcurrentTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}

class OkHttpFutureWebsocketTest
    extends WebSocketTest[Future]
    with WebSocketBufferOverflowTest[Future]
    with WebSocketConcurrentTest[Future] {
  override val backend: SttpBackend[Future, WebSockets] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def throwsWhenNotAWebSocket: Boolean = true
  override def bufferCapacity: Int = OkHttpBackend.DefaultWebSocketBufferCapacity.get

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Future[T]): Future[T] = {
    Future(blocking(Thread.sleep(interval.toMillis))).flatMap(_ => f).recoverWith {
      case _ if attempts > 0 => eventually(interval, attempts - 1)(f)
    }
  }

  override def concurrently[T](fs: List[() => Future[T]]): Future[List[T]] = Future.sequence(fs.map(_()))
}
