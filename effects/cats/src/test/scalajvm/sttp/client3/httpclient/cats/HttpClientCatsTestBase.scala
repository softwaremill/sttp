package sttp.client3.httpclient.cats

import cats.effect.IO
import org.scalatest.Suite
import sttp.capabilities.WebSockets
import sttp.client3._
import sttp.client3.impl.cats.{CatsTestBase, TestIODispatcher}

trait HttpClientCatsTestBase extends CatsTestBase with TestIODispatcher { this: Suite =>
  implicit val backend: SttpBackend[IO, WebSockets] =
    HttpClientCatsBackend[IO](dispatcher).unsafeRunSync()
}
