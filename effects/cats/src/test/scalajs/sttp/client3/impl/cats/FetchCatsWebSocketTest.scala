package sttp.client3.impl.cats

import cats.effect.IO
import sttp.capabilities
import sttp.client3.SttpBackend
import sttp.client3.testing.ConvertToFuture
import sttp.client3.testing.websocket.WebSocketTest

class FetchCatsWebSocketTest extends WebSocketTest[IO] with CatsTestBase {
  override val backend: SttpBackend[IO, capabilities.WebSockets] =
    FetchCatsBackend(convertFromFuture = convertFromFuture)
  override implicit val convertToFuture: ConvertToFuture[IO] = convertToFuture
}
