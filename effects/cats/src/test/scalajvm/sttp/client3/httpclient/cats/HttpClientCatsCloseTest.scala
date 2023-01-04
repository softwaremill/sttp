package sttp.client3.httpclient.cats

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class HttpClientCatsCloseTest extends AsyncFreeSpec with Matchers {
  "cats-effect" - {
    "continue working after the backend is closed" in {
      HttpClientCatsBackend
        .resource[IO]()
        .use(_ => IO.unit)
        .flatMap(_ => IO(succeed).start.flatMap(_.joinWith(IO(fail())))) // the cats executor should still be open
        .unsafeToFuture()
    }
  }
}
