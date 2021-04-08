package sttp.client3.httpclient.fs2

import cats.effect.IO
import org.scalatest.Suite
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.impl.cats.{CatsTestBase, TestIODispatcher}

trait HttpClientFs2TestBase extends CatsTestBase with TestIODispatcher { this: Suite =>
  implicit val backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets] =
    HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()
}
