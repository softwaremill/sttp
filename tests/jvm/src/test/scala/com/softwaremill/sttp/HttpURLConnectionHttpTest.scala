package com.softwaremill.sttp

import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

class HttpURLConnectionHttpTest extends HttpTest[Id] {

  override implicit val backend: SttpBackend[Id, Nothing] =
    HttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Id] =
    ConvertToFuture.id
}
