package sttp.client4.armeria.cats

import cats.effect.IO
import sttp.client4._
import sttp.client4.impl.cats.{CatsRetryTest, CatsTestBase}
import sttp.client4.testing.HttpTest

class ArmeriaCatsHttpTest extends HttpTest[IO] with CatsRetryTest with CatsTestBase {
  override val backend: Backend[IO] = ArmeriaCatsBackend[IO]()

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send(backend).toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
  override def supportsEmptyContentEncoding = false
}
