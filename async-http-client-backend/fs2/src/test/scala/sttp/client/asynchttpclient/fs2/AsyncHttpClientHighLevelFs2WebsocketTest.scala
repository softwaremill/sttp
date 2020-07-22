package sttp.client.asynchttpclient.fs2

import cats.effect.IO
import sttp.client._
import sttp.client.asynchttpclient.{AsyncHttpClientHighLevelWebsocketTest, WebSocketHandler}
import sttp.client.impl.cats.CatsTestBase
import sttp.client.ws.WebSocket

import scala.concurrent.duration._
import cats.implicits._

class AsyncHttpClientHighLevelFs2WebsocketTest extends AsyncHttpClientHighLevelWebsocketTest[IO] with CatsTestBase {
  implicit val backend: SttpBackend[IO, Any, WebSocketHandler] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  override def createHandler: Option[Int] => IO[WebSocketHandler[WebSocket[IO]]] = Fs2WebSocketHandler[IO](_)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] = {
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
      }
    }
    tryWithCounter(0)
  }
}
