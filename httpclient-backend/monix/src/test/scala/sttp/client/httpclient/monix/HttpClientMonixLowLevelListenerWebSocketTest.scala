package sttp.client.httpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client.SttpBackend
import sttp.client.httpclient.{HttpClientLowLevelListenerWebSocketTest, WebSocketHandler}
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.ConvertToFuture

class HttpClientMonixLowLevelListenerWebSocketTest extends HttpClientLowLevelListenerWebSocketTest[Task] {
  override implicit val backend: SttpBackend[Task, _, WebSocketHandler] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
