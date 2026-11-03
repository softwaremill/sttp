package sttp.client4.testing.streaming

import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory, TimeUnit, TimeoutException}
import scala.concurrent.{Future, Promise}

trait StreamingTestExtensions[F[_], S] { self: StreamingTest[F, S] =>

  /** Fails if `f` doesn't complete within 10 seconds. Unlike `timeoutToNone`, doesn't wait for any cancellation to
    * complete, so that a backend which doesn't cancel properly fails the test, instead of hanging it.
    */
  protected def withDeadline[T](f: Future[T], waitingFor: String): Future[T] = {
    val result = Promise[T]()
    val timeout = StreamingTestExtensions.scheduler.schedule(
      new Runnable {
        override def run(): Unit = { result.tryFailure(new TimeoutException(s"Timed out waiting for $waitingFor")); () }
      },
      10,
      TimeUnit.SECONDS
    )
    f.onComplete { r =>
      timeout.cancel(false)
      result.tryComplete(r)
    }
    result.future
  }
}

object StreamingTestExtensions {
  private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r, "streaming-test-deadline")
      t.setDaemon(true)
      t
    }
  })
}
