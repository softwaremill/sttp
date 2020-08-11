package sttp.client.okhttp

import sttp.client._
import sttp.client.monad.{FutureMonad, MonadError}
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.{WebSocketBufferOverflowTest, WebSocketTest}

import scala.concurrent.duration._
import scala.concurrent.{Future, blocking}

class OkHttpFutureWebsocketTest extends WebSocketTest[Future] with WebSocketBufferOverflowTest[Future] {
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
}
