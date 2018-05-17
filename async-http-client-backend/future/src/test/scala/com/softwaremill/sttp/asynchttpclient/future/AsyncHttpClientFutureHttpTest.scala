package com.softwaremill.sttp.asynchttpclient.future

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends HttpTest[Future] {

  override implicit val backend: SttpBackend[Future, Nothing] =
    AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future
}
