package sttp.client4.impl.fs2

import sttp.client4.impl.cats.CatsTestBase
import cats.effect.IO
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.WebSocketStreamBackend
import sttp.client4.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class FetchFs2WebSocketTest
    extends WebSocketTest[IO]
    with WebSocketStreamingTest[IO, Fs2Streams[IO]]
    with CatsTestBase {
  implicit override def executionContext: ExecutionContext = queue
  override def throwsWhenNotAWebSocket: Boolean = true
  override def supportsReadingWebSocketResponseHeaders: Boolean = false
  override def supportsReadingSubprotocolWebSocketResponseHeader: Boolean = false

  override val backend: WebSocketStreamBackend[IO, Fs2Streams[IO]] = FetchFs2Backend()

  override val streams: Fs2Streams[IO] = Fs2Streams[IO]

  override def prepend(item: WebSocketFrame.Text)(
      to: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => Stream(item) ++ rest)

  override def fromTextPipe(function: String => WebSocketFrame): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    Fs2WebSockets.fromTextPipe(function)

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] = _.map(f).collect { case Some(v) =>
    v
  }
}
