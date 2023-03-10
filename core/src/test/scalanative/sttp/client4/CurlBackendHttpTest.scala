package sttp.client4

import sttp.client4.testing.SyncHttpTest

class CurlBackendHttpTest extends SyncHttpTest {
  override implicit val backend: SyncBackend = CurlBackend(verbose = true)
}
