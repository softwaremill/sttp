package sttp.client4.impl.cats

import cats.effect.IO
import sttp.client4.WebSocketBackend
import sttp.client4.testing.websocket.WebSocketTest

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.queue

class FetchCatsWebSocketTest extends WebSocketTest[IO] with CatsTestBase {
  implicit override def executionContext: ExecutionContext = queue
  override def throwsWhenNotAWebSocket: Boolean = true

  override val backend: WebSocketBackend[IO] = FetchCatsBackend()
}
