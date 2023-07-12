package sttp.client4

import sttp.client4.httpclient.HttpClientWebsocketSyncBackend
import sttp.client4.monad.IdMonad
import sttp.client4.testing.websocket.WebSocketTest
import sttp.client4.testing.ConvertToFuture
import sttp.monad.MonadError

class HttpClientWebsocketSyncTest extends WebSocketTest[Identity] {
  override val backend: WebSocketBackend[Identity] = HttpClientWebsocketSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
  override implicit val monad: MonadError[Identity] = IdMonad

  override def throwsWhenNotAWebSocket: Boolean = true
}
