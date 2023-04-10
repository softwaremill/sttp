package sttp.client4.httpclient.fs2

import cats.effect.IO
import org.scalatest.Suite
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}

trait HttpClientFs2TestBase extends CatsTestBase with TestIODispatcher { this: Suite =>
  implicit val backend: WebSocketStreamBackend[IO, Fs2Streams[IO]] =
    HttpClientFs2Backend[IO](dispatcher).unsafeRunSync()
}
