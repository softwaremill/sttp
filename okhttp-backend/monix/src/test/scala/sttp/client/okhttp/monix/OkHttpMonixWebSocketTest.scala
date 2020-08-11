package sttp.client.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.client._
import sttp.client.impl.monix.{MonixStreams, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.okhttp.OkHttpBackend
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.websocket.{WebSocketBufferOverflowTest, WebSocketStreamingTest, WebSocketTest}
import sttp.model.ws.WebSocketFrame

import scala.concurrent.duration._

class OkHttpMonixWebSocketTest
    extends WebSocketTest[Task]
    with WebSocketStreamingTest[Task, MonixStreams]
    with WebSocketBufferOverflowTest[Task] {
  override val streams: MonixStreams = MonixStreams
  override val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def throwsWhenNotAWebSocket: Boolean = true
  override def bufferCapacity: Int = OkHttpBackend.DefaultWebSocketBufferCapacity.get

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] = _.map(f)

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)
  }
}
