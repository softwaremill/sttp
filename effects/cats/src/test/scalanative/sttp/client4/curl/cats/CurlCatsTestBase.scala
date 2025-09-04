package sttp.client4.curl.cats

import cats.effect.IO
import org.scalatest.Suite
import sttp.client4.Backend
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}

trait CurlCatsTestBase extends CatsTestBase with TestIODispatcher { this: Suite =>
  implicit val backend: Backend[IO] = CurlCatsBackend[IO]()
}