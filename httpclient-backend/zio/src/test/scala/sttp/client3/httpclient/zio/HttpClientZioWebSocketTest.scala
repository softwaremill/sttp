package sttp.client3.httpclient.zio

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.BlockingZioStreams
import sttp.client3._
import sttp.client3.impl.zio.ZioWebSockets.PipeR
import sttp.client3.impl.zio.{RIOMonadAsyncError, ZioTestBase, ZioWebSockets}
import sttp.monad.MonadError
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame
import zio.blocking.Blocking
import zio.stream._

class HttpClientZioWebSocketTest
    extends WebSocketTest[BlockingTask]
    with WebSocketStreamingTest[BlockingTask, BlockingZioStreams]
    with ZioTestBase {
  implicit val backend: SttpBackend[BlockingTask, BlockingZioStreams with WebSockets] =
    runtime.unsafeRun(HttpClientZioBackend())
  implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture
  implicit val monad: MonadError[BlockingTask] = new RIOMonadAsyncError
  override val streams: BlockingZioStreams = BlockingZioStreams

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): BlockingZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    in => in.mapConcat(m => f(m).toList)

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): BlockingZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    ZioWebSockets.fromTextPipe[Blocking](function)

  override def prepend(item: WebSocketFrame.Text)(
      to: PipeR[Blocking, WebSocketFrame.Data[_], WebSocketFrame]
  ): BlockingZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => ZStream(item) ++ rest)
}
