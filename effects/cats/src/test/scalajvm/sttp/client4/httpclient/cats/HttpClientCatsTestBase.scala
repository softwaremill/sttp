package sttp.client4.httpclient.cats

import cats.effect.IO
import org.scalatest.Suite
import sttp.client4._
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}

trait HttpClientCatsTestBase extends CatsTestBase with TestIODispatcher { this: Suite =>
  implicit val backend: WebSocketBackend[IO] =
    HttpClientCatsBackend[IO](dispatcher).unsafeRunSync()
}
