package sttp.client

import sttp.client.testing.SyncHttpTest

class CurlBackendHttpTest extends SyncHttpTest {
  override implicit lazy val backend: SttpBackend[Identity, Any] = CurlBackend(verbose = true)
}
