package sttp.client.asynchttpclient.fs2

import cats.effect.IO
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.cats.CatsTestBase
import sttp.client.testing.HttpTest

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] with CatsTestBase {
  override implicit val backend: SttpBackend[IO, Nothing, NothingT] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()
}
