package sttp.client.httpclient

import sttp.client._
import sttp.client.testing.ConvertToFuture

import scala.concurrent.Future

class HttpClientFutureLowLevelListenerWebSocketTest extends HttpClientLowLevelListenerWebSocketTest[Future] {
  override implicit val backend: SttpBackend[Future, Any, WebSocketHandler] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
}
