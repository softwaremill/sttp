package com.softwaremill.sttp

import com.softwaremill.sttp.testing.SyncHttpTest

class CurlBackendHttpTest extends SyncHttpTest {
  override implicit lazy val backend: SttpBackend[Identity, Nothing] = CurlBackend(verbose = true)
}
