package sttp.client3.testing

import org.scalatest.freespec.AsyncFreeSpecLike
import org.scalatest.{Failed, FutureOutcome}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

// this needs to be added here, as it doesn't compile on native
trait AsyncRetries extends AsyncFreeSpecLike {
  // TODO: on GH Actions some tests sometimes timeout. For lack of a better solution, retrying them, but this needs proper fixing one day.
  override def withFixture(test: NoArgAsyncTest): FutureOutcome =
    new FutureOutcome(super.withFixture(test).toFuture.flatMap {
      case Failed(e) =>
        info(s"Test: ${test.name}, failed with: ${e.getMessage}, retrying.", Some(e))
        super.withFixture(test).toFuture
      case o => Future.successful(o)
    })

  def retry[T](n: Int)(f: => Future[T]): Future[T] = {
    Try(f) match {
      case Failure(exception) => if (n == 0) Future.failed(exception) else retry(n - 1)(f)
      case Success(value) =>
        value.recoverWith {
          case e if n > 0 =>
            info(s"Failed with: ${e.getMessage}, retrying ($n).", Some(e))
            retry(n - 1)(f)
        }
    }
  }
}
