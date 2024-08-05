package sttp.client4.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4._
import sttp.client4.impl.zio.ZioWebSockets.PipeR
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioTestBase, ZioWebSockets}
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.websocket.{WebSocketConcurrentTest, WebSocketStreamingTest, WebSocketTest}
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame
import zio.Task
import zio.stream._

class HttpClientZioWebSocketTest
    extends WebSocketTest[Task]
    with WebSocketStreamingTest[Task, ZioStreams]
    with WebSocketConcurrentTest[Task]
    with ZioTestBase {
  implicit val backend: WebSocketStreamBackend[Task, ZioStreams] = runtime.unsafeRun(HttpClientZioBackend())
  implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  implicit val monad: MonadError[Task] = new RIOMonadAsyncError
  override val streams: ZioStreams = ZioStreams

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    in => in.mapConcat(m => f(m).toList)

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    ZioWebSockets.fromTextPipe[Any](function)

  override def prepend(item: WebSocketFrame.Text)(
      to: PipeR[Any, WebSocketFrame.Data[_], WebSocketFrame]
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => ZStream(item) ++ rest)

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.collectAllPar(fs.map(_()))

  override def supportsReadingWebSocketResponseHeaders: Boolean = false
}
