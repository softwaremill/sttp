package com.softwaremill.sttp.asynchttpclient.cats

import cats.effect.{ContextShift, IO}
import com.softwaremill.sttp._
import com.softwaremill.sttp.impl.cats.convertCatsIOToFuture
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientCatsHttpTest extends HttpTest[IO] {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  override implicit val backend: SttpBackend[IO, Nothing] = AsyncHttpClientCatsBackend()
  override implicit val convertToFuture: ConvertToFuture[IO] = convertCatsIOToFuture

  "illegal url exceptions" - {
    "should be wrapped in the effect wrapper" in {
      sttp.get(uri"ps://sth.com").send().toFuture().failed.map { e =>
        e shouldBe a[IllegalArgumentException]
      }
    }
  }
}
