package sttp.client3.httpclient.cats

import cats.effect.IO
import cats.implicits._
import sttp.client3.testing.websocket.{WebSocketConcurrentTest, WebSocketTest}

class HttpClientCatsWebSocketTest
    extends WebSocketTest[IO]
    with WebSocketConcurrentTest[IO]
    with HttpClientCatsTestBase {

  override def concurrently[T](fs: List[() => IO[T]]): IO[List[T]] = fs.map(_()).parSequence
}
