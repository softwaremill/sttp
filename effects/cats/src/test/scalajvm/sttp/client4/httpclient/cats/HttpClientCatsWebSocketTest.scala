package sttp.client4.httpclient.cats

import cats.effect.IO
import cats.implicits._
import sttp.client4.testing.websocket.{WebSocketConcurrentTest, WebSocketTest}

class HttpClientCatsWebSocketTest
    extends WebSocketTest[IO]
    with WebSocketConcurrentTest[IO]
    with HttpClientCatsTestBase {

  override def concurrently[T](fs: List[() => IO[T]]): IO[List[T]] = fs.map(_()).parSequence

  override def supportsReadingWebSocketResponseHeaders: Boolean = false
}
