package com.softwaremill.sttp

import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.HttpTest

class HttpURLConnectionBackendHttpTest extends HttpTest[Id] {

  override implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Id] = ConvertToFuture.id
}
