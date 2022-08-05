package sttp.client3.impl.zio

import zio.Task
import zio.stream.ZStream
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class FetchZioWebSocketTest extends WebSocketTest[Task] with WebSocketStreamingTest[Task, ZioStreams] with ZioTestBase {
  implicit override def executionContext: ExecutionContext = queue
  override def throwsWhenNotAWebSocket: Boolean = true

  override val backend: SttpBackend[Task, ZioStreams with WebSockets] = FetchZioBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override implicit def monad: MonadError[Task] = new RIOMonadAsyncError[Any]

  override val streams: ZioStreams = ZioStreams

  override def prepend(item: WebSocketFrame.Text)(
      to: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => ZStream.succeed(item) ++ rest)

  override def fromTextPipe(function: String => WebSocketFrame): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    ZioWebSockets.fromTextPipe(function)

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] = in => in.mapConcat(m => f(m).toList)
}
