package sttp.client4.testing.streaming

import scala.language.higherKinds
import sttp.client4.testing.AsyncExecutionContext

import java.util.concurrent.TimeoutException
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.timers

trait StreamingTestExtensions[F[_], S] extends AsyncExecutionContext { self: StreamingTest[F, S] =>

  /** Fails if `f` doesn't complete within 10 seconds. Unlike `timeoutToNone`, doesn't wait for any cancellation to
    * complete, so that a backend which doesn't cancel properly fails the test, instead of hanging it.
    */
  protected def withDeadline[T](f: Future[T], waitingFor: String): Future[T] = {
    val result = Promise[T]()
    val timeout = timers.setTimeout(10000) {
      result.tryFailure(new TimeoutException(s"Timed out waiting for $waitingFor")): Unit
    }
    f.onComplete { r =>
      timers.clearTimeout(timeout)
      result.tryComplete(r)
    }
    result.future
  }
}
