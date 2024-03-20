package sttp.client4.asynchttpclient.cats

import cats.effect.IO
import sttp.client4._
import sttp.client4.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client4.impl.cats.CatsTestBase

class AsyncHttpClientCatsHttpTest extends AsyncHttpClientHttpTest[IO] with CatsTestBase {
  override val backend: Backend[IO] = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send(backend).toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }
}
