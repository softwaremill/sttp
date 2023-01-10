package sttp.client3.impl.cats

import cats.effect.IO
import sttp.client3._
import sttp.client3.testing.HttpTest

import sttp.client3.testing.HttpTest.endpoint
import sttp.model.StatusCode

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

trait CatsRetryTest { this: HttpTest[IO] =>
  def retry[A](
      task: IO[A],
      delay: FiniteDuration = 100.millis,
      retriesLeft: Int = 5
  ): IO[A] =
    task
      .onError(e => IO.delay(println(s"Received error: $e, retries left = $retriesLeft")))
      .handleErrorWith { error =>
        if (retriesLeft > 0)
          IO.sleep(delay) *> retry(task, delay, retriesLeft - 1)
        else
          IO.raiseError[A](error)
      }

  "retry test" - {
    "should call the HTTP server twice" in {
      val tag = Random.nextString(10)
      val check = backend.send(basicRequest.get(uri"$endpoint/retry?tag=$tag")).map { response =>
        response.code shouldBe StatusCode.Ok
      }

      retry(check).toFuture()
    }
  }
}
