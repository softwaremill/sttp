package sttp.client.httpclient.fs2

import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.client._
import sttp.client.impl.cats.CatsTestBase
import sttp.client.impl.fs2.Fs2Streams

trait HttpClientFs2TestBase extends CatsTestBase {
  implicit val backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets] =
    HttpClientFs2Backend[IO](blocker).unsafeRunSync()
}
