package com.softwaremill.sttp.asynchttpclient.cats

import cats.effect.IO
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientCatsHttpTest extends HttpTest[IO] {

  override implicit val backend: SttpBackend[IO, Nothing] =
    AsyncHttpClientCatsBackend()
  override implicit val convertToFuture: ConvertToFuture[IO] =
    com.softwaremill.sttp.impl.cats.convertToFuture
}
