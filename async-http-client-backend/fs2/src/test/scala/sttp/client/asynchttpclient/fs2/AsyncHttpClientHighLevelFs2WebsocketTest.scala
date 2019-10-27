package sttp.client.asynchttpclient.fs2

import cats.effect.{ContextShift, IO, Timer}
import sttp.client._
import sttp.client.asynchttpclient.{AsyncHttpClientHighLevelWebsocketTest, WebSocketHandler}
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.ws.WebSocket

import scala.concurrent.Future
import scala.concurrent.duration._
import cats.implicits._

class AsyncHttpClientHighLevelFs2WebsocketTest extends AsyncHttpClientHighLevelWebsocketTest[IO] {
  implicit val backend: SttpBackend[IO, Nothing, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
  implicit val convertToFuture: ConvertToFuture[IO] = new ConvertToFuture[IO] {
    override def toFuture[T](value: IO[T]): Future[T] = value.unsafeToFuture()
  }
  override implicit val monad: MonadError[IO] = new CatsMonadAsyncError[IO]
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(implicitly)
  implicit lazy val timer: Timer[IO] = IO.timer(implicitly)

  override def createHandler: Option[Int] => WebSocketHandler[WebSocket[IO]] =
    Fs2WebSocketHandler[IO](_).unsafeRunSync()

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] = {
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
      }
    }
    tryWithCounter(0)
  }
}
