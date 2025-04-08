package sttp.client4.impl.cats

import cats.effect.IO
import org.scalatest.freespec.AsyncFreeSpecLike
import sttp.client4._
import sttp.client4.testing.HttpTest
import sttp.client4.testing.HttpTest.endpoint
import sttp.model.StatusCode

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

trait CatsRetryTest extends AsyncFreeSpecLike { this: HttpTest[IO] =>
  private def retry[A](task: IO[A], delay: FiniteDuration, retries: Int): IO[A] =
    task
      .onError { case e => IO.delay(println(s"Received error: $e, retries left = $retries")) }
      .handleErrorWith { error =>
        if (retries > 0)
          IO.sleep(delay) *> retry(task, delay, retries - 1)
        else
          IO.raiseError[A](error)
      }

  "retry" - {
    "should call the HTTP server up to 5 times" in {
      val tag = Random.nextString(10)
      val check = basicRequest.get(uri"$endpoint/retry?tag=$tag").send(backend).map { response =>
        response.code shouldBe StatusCode.Ok
      }

      retry(check, delay = 100.millis, retries = 5).toFuture()
    }
  }
}
