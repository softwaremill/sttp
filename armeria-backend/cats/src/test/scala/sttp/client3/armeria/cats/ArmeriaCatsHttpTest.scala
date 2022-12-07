package sttp.client3.armeria.cats

import cats.effect.IO
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.scalatest.Assertion
import sttp.client3._
import sttp.client3.impl.cats.CatsTestBase
import sttp.client3.testing.HttpTest
import sttp.model.StatusCode

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class ArmeriaCatsHttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: SttpBackend[IO, Any] = ArmeriaCatsBackend[IO]()

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send(backend).toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }

  val port = 8080
  val host = "localhost"

  implicit class RetryTask[A](task: IO[A]) {
    def retry(
        delay: FiniteDuration = 100.millis,
        retriesLeft: Int = 5
    ): IO[A] =
      task
        .onError(e => IO.delay(println(s"Received error: $e, retries left = $retriesLeft")))
        .handleErrorWith { error =>
          if (retriesLeft > 0)
            IO.sleep(delay) *> retry(delay, retriesLeft - 1)
          else
            IO.raiseError[A](error)
        }
  }

  def setRespondWithCode(code: Int, howManyTimes: Int): Unit = {
    new MockServerClient(host, port)
      .when(
        request()
          .withPath("/api-test")
          .withMethod("GET"),
        Times.exactly(howManyTimes)
      )
      .respond(
        response()
          .withStatusCode(code)
      )
  }

  "retry test" - {
    "should call the HTTP server twice" in {
      var mockServer: ClientAndServer = null
      mockServer = startClientAndServer(port)
      // given
      setRespondWithCode(401, howManyTimes = 2)
      setRespondWithCode(400, howManyTimes = 1)
      setRespondWithCode(200, howManyTimes = 1)

      // when
      val result: IO[Response[Either[String, String]]] =
        backend.send(basicRequest.get(uri"http://$host:$port/api-test"))

      // then
      val check: IO[Assertion] = result.map { response =>
        response.code shouldBe StatusCode.Ok
      }

      check
        .retry()
        .map(e => {
          mockServer.stop()
          e
        })
        .toFuture()
    }
  }

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
}
