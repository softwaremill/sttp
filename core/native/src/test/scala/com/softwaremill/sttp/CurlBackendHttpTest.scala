package com.softwaremill.sttp

import com.softwaremill.sttp.testing.SyncHttpTest

class CurlBackendHttpTest extends SyncHttpTest {
  override implicit lazy val backend: SttpBackend[Id, Nothing] = CurlBackend()
}
