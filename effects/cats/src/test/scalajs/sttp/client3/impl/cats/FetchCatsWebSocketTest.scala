package sttp.client3.impl.cats

import cats.effect.IO
import sttp.client3.WebSocketBackend
import sttp.client3.testing.websocket.WebSocketTest

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.queue

class FetchCatsWebSocketTest extends WebSocketTest[IO] with CatsTestBase {
  implicit override def executionContext: ExecutionContext = queue
  override def throwsWhenNotAWebSocket: Boolean = true

  override val backend: WebSocketBackend[IO] = FetchCatsBackend()
}
