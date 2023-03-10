package sttp.client4.asynchttpclient.cats

import cats.effect.IO
import sttp.client4._
import sttp.client4.impl.cats.CatsTestBase
import sttp.client4.testing.HttpTest

class AsyncHttpClientCatsHttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: Backend[IO] = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send(backend).toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }

  override def throwsExceptionOnUnsupportedEncoding = false
  override def supportsAutoDecompressionDisabling = false
}
