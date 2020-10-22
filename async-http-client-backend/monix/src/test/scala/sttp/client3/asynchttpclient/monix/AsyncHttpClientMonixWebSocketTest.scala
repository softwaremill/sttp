package sttp.client3.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client3._
import sttp.client3.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client3.impl.monix.{MonixWebSockets, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.monad.MonadError
import sttp.client3.testing.ConvertToFuture
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientMonixWebSocketTest extends AsyncHttpClientWebSocketTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    in => in.concatMapIterable(m => f(m).toList)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)
  }

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] = MonixWebSockets.fromTextPipe(function)

  override def prepend(item: WebSocketFrame.Text)(
      to: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    to.andThen(rest => Observable.now(item) ++ rest)
}
