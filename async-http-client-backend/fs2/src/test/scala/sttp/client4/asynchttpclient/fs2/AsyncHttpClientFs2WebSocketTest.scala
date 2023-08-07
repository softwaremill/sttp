package sttp.client4.asynchttpclient.fs2

import cats.effect.IO
import cats.implicits._
import fs2.Pipe
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}
import sttp.client4.impl.fs2.Fs2WebSockets
import sttp.ws.WebSocketFrame

import scala.concurrent.duration._

class AsyncHttpClientFs2WebSocketTest
    extends AsyncHttpClientWebSocketTest[IO, Fs2Streams[IO]]
    with TestIODispatcher
    with CatsTestBase {

  implicit val backend: WebSocketStreamBackend[IO, Fs2Streams[IO]] =
    AsyncHttpClientFs2Backend[IO](dispatcher = dispatcher).unsafeRunSync()

  override val streams: Fs2Streams[IO] = new Fs2Streams[IO] {}

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): fs2.Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = in => in.mapFilter(f)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => IO[T]): IO[T] = {
    def tryWithCounter(i: Int): IO[T] =
      (IO.sleep(interval) >> f).recoverWith {
        case _: Exception if i < attempts => tryWithCounter(i + 1)
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

  override def concurrently[T](fs: List[() => IO[T]]): IO[List[T]] = fs.map(_()).parSequence
}
