package sttp.client.asynchttpclient.zio

import sttp.client._
import sttp.client.asynchttpclient.{AHCWebsocketHandlerTest, WebSocketHandler}
import sttp.client.impl.zio.{TaskMonadAsyncError, convertZioIoToFuture, runtime}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.client.ws.WebSocket
import zio.clock.Clock
import zio.{Schedule, Task, ZIO}
import zio.duration._

class ZioWebsocketHandlerTest extends AHCWebsocketHandlerTest[Task] {
  override implicit val backend: SttpBackend[Task, Nothing, WebSocketHandler] =
    runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioIoToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def createHandler: Option[Int] => WebSocketHandler[WebSocket[Task]] =
    bufferCapacity => runtime.unsafeRun(ZioWebSocketHandler(bufferCapacity))

  override def eventually[T](f: => Task[T]): Task[T] = {
    ZIO.sleep(10 millis).andThen(f).retry(Schedule.recurs(100)).provide(Clock.Live)
  }
}
