package sttp.client4.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientWebSocketTest
import sttp.client4.impl.monix.{convertMonixTaskToFuture, TaskMonadAsyncError}
import sttp.client4.testing.ConvertToFuture
import sttp.monad.MonadError

import scala.concurrent.duration.FiniteDuration

class AsyncHttpClientMonixWebSocketTest extends AsyncHttpClientWebSocketTest[Task] {
  override val backend: WebSocketBackend[Task] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
  override implicit val monad: MonadError[Task] = TaskMonadAsyncError

  override def eventually[T](interval: FiniteDuration, attempts: Int)(f: => Task[T]): Task[T] =
    (Task.sleep(interval) >> f).onErrorRestart(attempts.toLong)

  override def concurrently[T](fs: List[() => Task[T]]): Task[List[T]] = Task.parSequence(fs.map(_()))
}
