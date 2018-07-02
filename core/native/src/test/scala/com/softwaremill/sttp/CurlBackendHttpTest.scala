package com.softwaremill.sttp

import com.softwaremill.sttp.testing.NativeHttpTest

class CurlBackendHttpTest extends NativeHttpTest {
  override implicit lazy val backend: SttpBackend[Id, Nothing] = CurlBackend()
}
