package sttp.client3.impl.cats

import cats.effect.IO
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.client3.testing.websocket.WebSocketTest

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.queue

class FetchCatsWebSocketTest extends WebSocketTest[IO] with CatsTestBase {
  implicit override def executionContext: ExecutionContext = queue

  override val backend: SttpBackend[IO, capabilities.WebSockets] =
    FetchCatsBackend(convertFromFuture = convertFromFuture)
}
