package sttp.client4

import sttp.client4.curl.CurlBackend
import sttp.client4.testing.SyncBackendStub

object DefaultSyncBackend {

  /** Creates a default synchronous backend, which is currently based on [[CurlBackend]]. */
  def apply(): SyncBackend = CurlBackend()

  /** Create a stub backend for testing. See [[SyncBackendStub]] for details on how to configure stub responses. */
  def stub: SyncBackendStub = SyncBackendStub
}
