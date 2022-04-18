package sttp.client3.httpclient.fs2

import cats.effect.IO
import cats.implicits._
import fs2.Pipe
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.impl.fs2.Fs2WebSockets
import sttp.client3.testing.websocket.{WebSocketConcurrentTest, WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame

class HttpClientFs2WebSocketTest
    extends WebSocketTest[IO]
    with WebSocketStreamingTest[IO, Fs2Streams[IO]]
    with WebSocketConcurrentTest[IO]
    with HttpClientFs2TestBase {
  override val streams: Fs2Streams[IO] = new Fs2Streams[IO] {}

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): fs2.Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = in => in.mapFilter(f)

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] =
    Fs2WebSockets.fromTextPipe[IO](function)

  override def prepend(
      item: WebSocketFrame.Text
  )(to: Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame]): Pipe[IO, WebSocketFrame.Data[_], WebSocketFrame] = {
    to.andThen(rest => fs2.Stream.eval(item.pure[IO]) ++ rest)
  }

  override def concurrently[T](fs: List[() => IO[T]]): IO[List[T]] = fs.map(_()).parSequence
}
