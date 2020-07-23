package sttp.client.httpclient

import sttp.client._
import sttp.client.testing.ConvertToFuture

class HttpClientSyncLowLevelListenerWebSocketTest extends HttpClientLowLevelListenerWebSocketTest[Identity] {
  implicit val backend: SttpBackend[Identity, Any, WebSocketHandler] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
