package sttp.client3.armeria.cats

import cats.effect.IO
import sttp.client3._
import sttp.client3.impl.cats.CatsTestBase
import sttp.client3.testing.HttpTest

class ArmeriaCatsHttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: SttpBackend[IO, Any] = ArmeriaCatsBackend[IO]()

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send(backend).toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportAutoDecompressionDisabling = false
}
