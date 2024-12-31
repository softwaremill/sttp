package sttp.client4

import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.testing.WebSocketSyncBackendStub

object DefaultSyncBackend {

  /** Creates a default synchronous backend with the given `options`, which is currently based on
    * [[HttpClientSyncBackend]].
    */
  def apply(options: BackendOptions = BackendOptions.Default): WebSocketSyncBackend = HttpClientSyncBackend(options)

  /** Create a stub backend for testing. See [[WebSocketSyncBackendStub]] for details on how to configure stub
    * responses.
    */
  def stub: WebSocketSyncBackendStub = WebSocketSyncBackendStub
}
