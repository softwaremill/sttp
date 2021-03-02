package sttp.client3.impl.monix

import monix.eval.Task
import monix.reactive.Observable
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client3.SttpBackend
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class FetchMonixWebSocketTest extends WebSocketTest[Task] with WebSocketStreamingTest[Task, MonixStreams] {
  implicit override def executionContext: ExecutionContext = queue
  override def throwsWhenNotAWebSocket: Boolean = true

  override val backend: SttpBackend[Task, MonixStreams with WebSockets] = FetchMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override implicit def monad: MonadError[Task] = TaskMonadAsyncError

  override val streams: MonixStreams = MonixStreams

  override def prepend(item: WebSocketFrame.Text)(
      to: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => Observable.now(item) ++ rest)

  override def fromTextPipe(function: String => WebSocketFrame): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    MonixWebSockets.fromTextPipe(function)

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] = in => in.concatMapIterable(m => f(m).toList)
}
