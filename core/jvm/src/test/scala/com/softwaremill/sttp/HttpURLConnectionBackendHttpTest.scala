package com.softwaremill.sttp

import com.softwaremill.sttp.testing.ConvertToFuture
import com.softwaremill.sttp.testing.HttpTest

class HttpURLConnectionBackendHttpTest extends HttpTest[Identity] {

  override implicit val backend: SttpBackend[Identity, Nothing] = HttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
