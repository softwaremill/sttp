package com.softwaremill.sttp.okhttp

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class OkHttpFutureHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
