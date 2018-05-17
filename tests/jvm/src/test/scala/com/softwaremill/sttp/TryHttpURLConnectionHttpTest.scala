package com.softwaremill.sttp

import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

import scala.util.Try

class TryHttpURLConnectionHttpTest extends HttpTest[Try] {

  override implicit val backend: SttpBackend[Try, Nothing] =
    TryHttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Try] =
    ConvertToFuture.scalaTry
}
