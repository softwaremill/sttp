package com.softwaremill.sttp.akkahttp

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AkkaHttpClientHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing] =
    AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future
}
