package sttp.client.httpclient.fs2

import cats.effect.IO
import fs2.Stream
import sttp.client._
import sttp.client.httpclient.WebSocketHandler
import sttp.client.impl.cats.CatsTestBase

trait HttpClientFs2TestBase extends CatsTestBase {
  implicit val backend: SttpBackend[IO, Stream[IO, Byte], WebSocketHandler] =
    HttpClientFs2Backend[IO](blocker).unsafeRunSync()
}
