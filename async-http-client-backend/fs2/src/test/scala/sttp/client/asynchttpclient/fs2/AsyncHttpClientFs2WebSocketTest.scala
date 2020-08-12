package sttp.client.asynchttpclient.fs2

import cats.effect.IO
import cats.implicits._
import sttp.capabilities.WebSockets
import sttp.client._
import sttp.client.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client.impl.cats.CatsTestBase
import sttp.client.impl.fs2.Fs2Streams
import sttp.ws.WebSocketFrame

import scala.concurrent.duration._

class AsyncHttpClientFs2WebSocketTest extends AsyncHttpClientWebSocketTest[IO, Fs2Streams[IO]] with CatsTestBase {
  implicit val backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets] =
    AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  override val streams: Fs2Streams[IO] = new Fs2Streams[IO] {}

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): fs2.Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = _.map(f)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] = {
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
      }
    }
    tryWithCounter(0)
  }
}
