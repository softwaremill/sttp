package com.softwaremill.sttp

import com.softwaremill.sttp.testing.AbstractCurlBackendHttpTest
import com.softwaremill.sttp.testing.ConvertToFuture

class CurlBackendHttpTest extends AbstractCurlBackendHttpTest[Nothing] {
  override implicit lazy val backend: SttpBackend[Id, Nothing] = CurlBackend(verbose = true)
}
