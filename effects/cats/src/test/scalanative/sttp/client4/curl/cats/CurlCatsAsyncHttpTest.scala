package sttp.client4.curl.cats

import cats.effect.IO
import cats.syntax.all._
import sttp.client4.Backend
import sttp.client4.impl.cats.{CatsRetryTest, CatsTestBase}
import sttp.client4.testing.HttpTest

class CurlCatsAsyncHttpTest extends HttpTest[IO] with CatsTestBase with CatsRetryTest {
  override implicit val backend: Backend[IO] = CurlCatsAsyncBackend()
  override def supportsHostHeaderOverride = false
  override def supportsDeflateWrapperChecking = false
  override def supportsCancellation = false

  for (parallelism <- List(1, 4)) {
    s"curl async backend with parallelism = $parallelism" - {
      "handle many concurrent requests" in {
        CurlCatsAsyncBackend
          .resource(parallelism = parallelism)
          .use { b =>
            val one = postEchoExact.body("x").send(b).map(_.body)
            List.fill(300)(one).parSequence.map(_.toSet shouldBe Set(Right("x")))
          }
          .toFuture()
      }
    }
  }
}
