package sttp.client.asynchttpclient.fs2

import cats.effect.{Blocker, IO}
import cats.implicits._
import fs2.Pipe
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client._
import sttp.client.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client.impl.cats.CatsTestBase
import sttp.client.impl.fs2.Fs2WebSockets
import sttp.ws.WebSocketFrame

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class AsyncHttpClientFs2WebSocketTest extends AsyncHttpClientWebSocketTest[IO, Fs2Streams[IO]] with CatsTestBase {
  implicit val backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  override val streams: Fs2Streams[IO] = new Fs2Streams[IO] {}

  override def functionToPipe(
      initial: List[WebSocketFrame.Data[_]],
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): fs2.Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = in => fs2.Stream.emits(initial) ++ in.mapFilter(f)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] = {
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
      }
    }
    tryWithCounter(0)
  }

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] =
    Fs2WebSockets.fromTextPipe[IO](function)

  override def prepend(
      item: WebSocketFrame.Text
  )(to: Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame]): Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => fs2.Stream.eval(item.pure[IO]) ++ rest)
}
