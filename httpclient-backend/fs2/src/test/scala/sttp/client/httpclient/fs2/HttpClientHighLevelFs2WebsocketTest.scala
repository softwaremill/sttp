package sttp.client.httpclient.fs2

import cats.effect.{IO, Timer}
import cats.implicits._
import sttp.client._
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.cats.CatsMonadError
import sttp.client.monad.MonadError
import sttp.client.testing.websocket.HighLevelWebsocketTest
import sttp.client.ws.WebSocket

import scala.concurrent.duration._

class HttpClientHighLevelFs2WebsocketTest
    extends HighLevelWebsocketTest[IO, WebSocketHandler]
    with HttpClientFs2TestBase {

  override def createHandler: Option[Int] => IO[WebSocketHandler[WebSocket[IO]]] =
    Fs2WebSocketHandler[IO](_)

  it should "handle backpressure correctly" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .openWebsocketF(createHandler(Some(3)))
      .flatMap { response =>
        val ws = response.result
        send(ws, 1000) >> eventually(10.millis, 500) { ws.isOpen.map(_ shouldBe true) }
      }
      .toFuture()
  }

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] = {
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
      }
    }
    tryWithCounter(0)
  }

}
