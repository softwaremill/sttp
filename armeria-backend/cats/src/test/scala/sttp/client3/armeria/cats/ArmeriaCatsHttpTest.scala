package sttp.client3.armeria.cats

import cats.effect.IO
import sttp.client3._
import sttp.client3.impl.cats.CatsTestBase
import sttp.client3.testing.HttpTest
import sttp.client3.testing.HttpTest.endpoint
import sttp.model.StatusCode

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

class ArmeriaCatsHttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: SttpBackend[IO, Any] = ArmeriaCatsBackend[IO]()

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send(backend).toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }

  val host = "localhost"

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

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
}
