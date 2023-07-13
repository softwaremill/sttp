package sttp.client4

import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.monad.IdMonad
import sttp.client4.testing.websocket.WebSocketTest
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import sttp.monad.MonadError

class HttpClientSyncHttpTest extends WebSocketTest[Identity] {
  override val backend: WebSocketBackend[Identity] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
  override implicit val monad: MonadError[Identity] = IdMonad

  override def throwsWhenNotAWebSocket: Boolean = true
}
