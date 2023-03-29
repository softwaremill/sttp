package sttp.client4.okhttp

import sttp.client4._
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.websocket.{WebSocketBufferOverflowTest, WebSocketConcurrentTest, WebSocketTest}
import sttp.monad.{FutureMonad, MonadError}

import scala.concurrent.duration._
import scala.concurrent.{blocking, Future}

class OkHttpFutureWebsocketTest
    extends WebSocketTest[Future]
    with WebSocketBufferOverflowTest[Future]
    with WebSocketConcurrentTest[Future] {
  override val backend: WebSocketBackend[Future] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def throwsWhenNotAWebSocket: Boolean = true
  override def bufferCapacity: Int = OkHttpBackend.DefaultWebSocketBufferCapacity.get

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Future[T]): Future[T] =
    Future(blocking(Thread.sleep(interval.toMillis))).flatMap(_ => f).recoverWith {
      case _ if attempts > 0 => eventually(interval, attempts - 1)(f)
    }

  override def concurrently[T](fs: List[() => Future[T]]): Future[List[T]] = Future.sequence(fs.map(_()))
}
