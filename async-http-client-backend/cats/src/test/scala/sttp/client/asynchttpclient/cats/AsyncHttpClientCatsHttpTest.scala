package sttp.client.asynchttpclient.cats

import cats.effect.{ContextShift, IO}
import sttp.client.{SttpBackend, _}
import sttp.client.impl.cats.convertCatsIOToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientCatsHttpTest extends HttpTest[IO] {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  override implicit val backend: SttpBackend[IO, Nothing, NothingT] = AsyncHttpClientCatsBackend[IO]().unsafeRunSync()
  override implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      basicRequest.get(uri"ps://sth.com").send().toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }
}
