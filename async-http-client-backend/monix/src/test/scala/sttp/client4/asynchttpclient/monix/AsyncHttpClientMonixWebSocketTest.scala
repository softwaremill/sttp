package sttp.client4.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.capabilities.monix.MonixStreams
import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client4.impl.monix.{convertMonixTaskToFuture, MonixWebSockets, TaskMonadAsyncError}
import sttp.monad.MonadError
import sttp.client4.testing.ConvertToFuture
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientMonixWebSocketTest extends AsyncHttpClientWebSocketTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override val backend: WebSocketStreamBackend[Task, MonixStreams] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    in => in.concatMapIterable(m => f(m).toList)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] =
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] = MonixWebSockets.fromTextPipe(function)

  override def prepend(item: WebSocketFrame.Text)(
      to: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] =
    to.andThen(rest => Observable.now(item) ++ rest)

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.parSequence(fs.map(_()))
}
