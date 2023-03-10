package sttp.client4.testing

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.{Canceled, Failed, FutureOutcome}

import scala.concurrent.Future

/** Retries all tests up to the given number of attempts. */
trait RetryTests extends AsyncFreeSpec {
  val retries = 10

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = withFixture(test, retries)

  def withFixture(test: NoArgAsyncTest, count: Int): FutureOutcome = {
    val outcome = super.withFixture(test)
    new FutureOutcome(outcome.toFuture.flatMap {
      case Failed(_) | Canceled(_) =>
        if (count == 1) super.withFixture(test).toFuture
        else {
          info("Retrying a failed test: " + test.name)
          withFixture(test, count - 1).toFuture
        }
      case other => Future.successful(other)
    })
  }
}
