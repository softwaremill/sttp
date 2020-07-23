package sttp.client.httpclient.zio

import sttp.client.SttpBackend
import sttp.client.httpclient.{HttpClientLowLevelListenerWebSocketTest, WebSocketHandler}
import sttp.client.testing.ConvertToFuture
import sttp.client.impl.zio._

class HttpClientZioLowLevelListenerWebSocketTest extends HttpClientLowLevelListenerWebSocketTest[BlockingTask] {
  override implicit val backend: SttpBackend[BlockingTask, Any, WebSocketHandler] =
    runtime.unsafeRun(HttpClientZioBackend())
  override implicit val convertToFuture: ConvertToFuture[BlockingTask] = convertZioBlockingTaskToFuture
}
