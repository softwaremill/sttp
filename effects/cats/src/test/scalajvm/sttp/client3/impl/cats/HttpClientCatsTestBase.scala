package sttp.client3.impl.cats

import cats.effect.IO
import org.scalatest.Suite
import sttp.capabilities.WebSockets
import sttp.client3._

trait HttpClientCatsTestBase extends CatsTestBase with TestIODispatcher { this: Suite =>
  implicit val backend: SttpBackend[IO, WebSockets] =
    HttpClientCatsBackend[IO](dispatcher).unsafeRunSync()
}
