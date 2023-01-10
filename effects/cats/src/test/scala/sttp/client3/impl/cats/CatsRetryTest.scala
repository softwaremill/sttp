package sttp.client3.impl.cats

import cats.effect.IO
import sttp.client3._
import sttp.client3.testing.HttpTest

import sttp.client3.testing.HttpTest.endpoint
import sttp.model.StatusCode

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

trait CatsRetryTest { this: HttpTest[IO] =>
  private def retry[A](task: IO[A], delay: FiniteDuration, retries: Int): IO[A] =
    task
      .onError(e => IO.delay(println(s"Received error: $e, retries left = $retries")))
      .handleErrorWith { error =>
        if (retries > 0)
          IO.sleep(delay) *> retry(task, delay, retries - 1)
        else
          IO.raiseError[A](error)
      }

  "retry" - {
    "should call the HTTP server up to 5 times" in {
      val tag = Random.nextString(10)
      val check = backend.send(basicRequest.get(uri"$endpoint/retry?tag=$tag")).map { response =>
        response.code shouldBe StatusCode.Ok
      }

      retry(check, delay = 100.millis, retries = 5).toFuture()
    }
  }
}
