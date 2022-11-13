package sttp.client3.impl.cats

import cats.effect.IO
import cats.implicits._
import sttp.client3.testing.websocket.{WebSocketConcurrentTest, WebSocketStreamingTest, WebSocketTest}
import sttp.ws.WebSocketFrame

class HttpClientCatsWebSocketTest
    extends WebSocketTest[IO]
    with WebSocketConcurrentTest[IO]
    with HttpClientCatsTestBase {

  override def concurrently[T](fs: List[() => IO[T]]): IO[List[T]] = fs.map(_()).parSequence
}
