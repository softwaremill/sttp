package sttp.client3.httpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client3._
import sttp.client3.impl.monix.{MonixWebSockets, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.{WebSocketStreamingTest, WebSocketTest}
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

class HttpClientMonixWebSocketTest extends WebSocketTest[Task] with WebSocketStreamingTest[Task, MonixStreams] {
  implicit val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    HttpClientMonixBackend().runSyncUnsafe()
  implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  implicit val monad: MonadError[Task] = TaskMonadAsyncError
  override val streams: MonixStreams = MonixStreams

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    in => in.concatMapIterable(m => f(m).toList)

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] = MonixWebSockets.fromTextPipe(function)

  override def prepend(item: WebSocketFrame.Text)(
      to: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    to.andThen(rest => Observable.now(item) ++ rest)
}
