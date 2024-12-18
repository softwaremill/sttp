package sttp.client4.asynchttpclient.zio

import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioTestBase}
import sttp.client4.testing.ConvertToFuture
import sttp.monad.MonadError
import zio.{durationLong, Clock, Schedule, Task, ZIO}

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientZioWebSocketTest extends AsyncHttpClientWebSocketTest[Task] with ZioTestBase {
  override val backend: WebSocketBackend[Task] = AsyncHttpClientZioBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  override implicit val monad: MonadError[Task] = new RIOMonadAsyncError

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] =
    Clock
      .sleep(interval.toMillis.millis)
      .flatMap(_ => f)
      .retry(Schedule.recurs(attempts))

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = ZIO.collectAllPar(fs.map(_()))
}
