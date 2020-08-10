package sttp.client.okhttp.monix

import java.nio.channels.ClosedChannelException

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.client._
import sttp.client.impl.monix.{MonixStreams, TaskMonadAsyncError, convertMonixTaskToFuture}
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.okhttp.OkHttpBackend
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.WebSocketTest
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

import scala.concurrent.duration._

class OkHttpMonixWebSocketTest extends WebSocketTest[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams
  override implicit val backend: SttpBackend[Task, MonixStreams with WebSockets] =
    OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def throwsWhenNotAWebSocket: Boolean = true

  it should "error if incoming messages overflow the buffer" in {
    basicRequest
      .get(uri"$wsEndpoint/ws/echo")
      .response(asWebSocketAlways { ws: WebSocket[Task] =>
        send(ws, OkHttpBackend.DefaultWebSocketBufferCapacity.get + 1) >>
          eventually(10.millis, 500) {
            ws.isOpen.map(_ shouldBe false)
          }
      })
      .send()
      .map(_.body)
      .handleError {
        case _: ClosedChannelException => succeed.unit
      }
      .toFuture()
  }

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame] = _.map(f)

  private def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)
  }
}
