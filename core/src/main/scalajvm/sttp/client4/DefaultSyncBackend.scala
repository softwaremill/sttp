package sttp.client4

import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.testing.WebSocketBackendStub

object DefaultSyncBackend {

  /** Creates a default synchronous backend with the given `options`, which is currently based on
    * [[HttpClientSyncBackend]].
    */
  def apply(options: BackendOptions = BackendOptions.Default): WebSocketBackend[Identity] =
    HttpClientSyncBackend(options, identity, PartialFunction.empty)

  /** Create a stub backend for testing. See [[SyncBackendStub]] for details on how to configure stub responses. */
  def stub: WebSocketBackendStub[Identity] = WebSocketBackendStub.synchronous
}
