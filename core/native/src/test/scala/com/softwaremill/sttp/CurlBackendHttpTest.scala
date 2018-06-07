package com.softwaremill.sttp

import com.softwaremill.sttp.testing.AbstractCurlBackendHttpTest
import com.softwaremill.sttp.testing.ConvertToFuture

class CurlBackendHttpTest extends AbstractCurlBackendHttpTest[Id, Nothing] {
  override implicit lazy val convertToFuture: ConvertToFuture[Id] = ConvertToFuture.id

  override implicit lazy val backend: SttpBackend[Id, Nothing] = CurlBackend(verbose = true)
}
