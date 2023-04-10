package sttp.client4.impl.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.client4._
import sttp.client4.httpclient.monix.HttpClientMonixBackend
import sttp.client4.testing.ConvertToFuture
import sttp.client4.testing.websocket.{WebSocketConcurrentTest, WebSocketStreamingTest, WebSocketTest}
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

class HttpClientMonixWebSocketTest
    extends WebSocketTest[Task]
    with WebSocketStreamingTest[Task, MonixStreams]
    with WebSocketConcurrentTest[Task] {
  implicit val backend: WebSocketStreamBackend[Task, MonixStreams] =
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

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.parSequence(fs.map(_()))
}
