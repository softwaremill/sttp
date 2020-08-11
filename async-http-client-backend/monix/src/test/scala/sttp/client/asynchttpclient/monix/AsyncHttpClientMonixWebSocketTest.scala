package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.client._
import sttp.client.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client.impl.monix.{MonixStreams, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.model.ws.WebSocketFrame

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientMonixWebSocketTest extends AsyncHttpClientWebSocketTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] = _.map(f)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)
  }
}
