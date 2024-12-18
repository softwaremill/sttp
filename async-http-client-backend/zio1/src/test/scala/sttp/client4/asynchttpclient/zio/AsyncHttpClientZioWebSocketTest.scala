package sttp.client4.asynchttpclient.zio

import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client4.impl.zio.{RIOMonadAsyncError, ZioTestBase}
import sttp.client4.testing.ConvertToFuture
import sttp.monad.MonadError
import zio.clock.Clock
import zio.duration._
import zio.{Schedule, Task, ZIO}

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientZioWebSocketTest extends AsyncHttpClientWebSocketTest[Task] with ZioTestBase {
  override val backend: WebSocketBackend[Task] = runtime.unsafeRun(AsyncHttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture
  override implicit val monad: MonadError[Task] = new RIOMonadAsyncError

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] =
    ZIO.sleep(interval.toMillis.millis).andThen(f).retry(Schedule.recurs(attempts)).provideLayer(Clock.live)

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.collectAllPar(fs.map(_()))
}
