package sttp.client4

import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.testing.websocket.WebSocketTest
import sttp.client4.testing.ConvertToFuture
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

class HttpClientSyncWebSocketTest extends WebSocketTest[Identity] {
  override val backend: WebSocketSyncBackend = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
  override implicit val monad: MonadError[Identity] = IdentityMonad

  override def throwsWhenNotAWebSocket: Boolean = true
  // HttpClient doesn't expose the response headers for web sockets in any way
  override def supportsReadingWebSocketResponseHeaders: Boolean = false
}
