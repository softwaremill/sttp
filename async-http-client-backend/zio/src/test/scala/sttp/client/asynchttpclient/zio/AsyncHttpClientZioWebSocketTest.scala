package sttp.client.asynchttpclient.zio

import sttp.client._
import sttp.client.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client.impl.zio.{RIOMonadAsyncError, ZioStreams, convertZioTaskToFuture, runtime}
import sttp.client.monad.MonadError
import sttp.client.testing.ConvertToFuture
import sttp.model.ws.WebSocketFrame
import zio.clock.Clock
import zio.{Schedule, Task, ZIO}
import zio.duration._
import zio.stream.Transducer

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientZioWebSocketTest extends AsyncHttpClientWebSocketTest[Task, ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override implicit val backend: SttpBackend[Task, WebSockets with ZioStreams] =
    runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  override implicit val monad: MonadError[Task] = new RIOMonadAsyncError

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    ZIO.sleep(interval.toMillis.millis).andThen(f).retry(Schedule.recurs(attempts)).provideLayer(Clock.live)
  }

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => WebSocketFrame
  ): Transducer[Throwable, WebSocketFrame.Data[_], WebSocketFrame] =
    Transducer.identity.map(f)
}
