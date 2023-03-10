package sttp.client4.asynchttpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioTestBase, ZioWebSockets}
import sttp.client4.testing.ConvertToFuture
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame
import zio.stream._
import zio.{Clock, Schedule, Task, ZIO, durationLong}

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientZioWebSocketTest extends AsyncHttpClientWebSocketTest[Task, ZioStreams] with ZioTestBase {
  override val streams: ZioStreams = ZioStreams

  override val backend: WebSocketStreamBackend[Task, ZioStreams] =
    unsafeRunSyncOrThrow(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  override implicit val monad: MonadError[Task] = new RIOMonadAsyncError

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] = {
    Clock
      .sleep(interval.toMillis.millis)
      .flatMap(_ => f)
      .retry(Schedule.recurs(attempts))
  }

  override def functionToPipe(
      f: WebSocketFrame.Data[_] => Option[WebSocketFrame]
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    in => in.mapConcat(m => f(m).toList)

  override def fromTextPipe(
      function: String => WebSocketFrame
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    ZioWebSockets.fromTextPipe[Any](function)

  override def prepend(item: WebSocketFrame.Text)(
      to: ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): ZioStreams.Pipe[WebSocketFrame.Data[_], WebSocketFrame] =
    to.andThen(rest => ZStream(item) ++ rest)

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = ZIO.collectAllPar(fs.map(_()))
}
