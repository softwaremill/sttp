package com.softwaremill.sttp

import scala.util.Try

import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.HttpTest

class TryHttpURLConnectionHttpTest extends HttpTest[Try] {

  override implicit val backend: SttpBackend[Try, Nothing] = TryHttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Try] = ConvertToFuture.scalaTry
}
