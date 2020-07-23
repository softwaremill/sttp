package sttp.client.httpclient.zio

import sttp.client._
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest.wsEndpoint
import sttp.client.testing.websocket.HighLevelWebsocketTest
import sttp.client.ws.WebSocket
import zio.blocking.Blocking
import sttp.client.impl.zio._
import zio.clock.Clock
import zio.{Schedule, ZIO}

import scala.concurrent.duration._
import zio.duration.Duration

class HttpClientHighLevelZioWebsocketTest extends HighLevelWebsocketTest[BlockingTask, WebSocketHandler] {
  implicit val backend: SttpBackend[BlockingTask, BlockingZioStreams, WebSocketHandler] =
    runtime.unsafeRun(HttpClientZioBackend())
  implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture
  implicit val monad: MonadError[BlockingTask] = new RIOMonadAsyncError

  def createHandler: Option[Int] => BlockingTask[WebSocketHandler[WebSocket[BlockingTask]]] = _ => ZioWebSocketHandler()

  it should "handle backpressure correctly" in {
    new ConvertToFutureDecorator(
      basicRequest
        .get(uri"$wsEndpoint/ws/echo")
        .openWebsocketF(createHandler(None))
        .flatMap { response =>
          val ws = response.result
          send(ws, 1000).flatMap(_ =>
            eventually(10.millis, 500) {
              ws.isOpen.map(_ shouldBe true)
            }
          )
        }
    ).toFuture()
  }

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => BlockingTask[T]): BlockingTask[T] = {
    (ZIO.sleep(Duration.fromScala(interval)) *> f.retry(Schedule.recurs(attempts)))
      .provideSomeLayer[Blocking](Clock.live)
  }
}
