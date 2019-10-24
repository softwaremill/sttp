package sttp.client.httpclient

import sttp.client._
import sttp.client.monad.{IdMonad, MonadError}
import sttp.client.testing.ConvertToFuture

class HttpClientSyncWebsocketTest extends HttpClientWebsocketTest[Identity] {
  implicit val backend: SttpBackend[Identity, Nothing, WebSocketHandler] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
  override implicit val monadError: MonadError[Identity] = IdMonad
}
