package sttp.client.asynchttpclient.fs2

import cats.effect.IO
import sttp.client.SttpBackend
import sttp.client.impl.cats.CatsTestBase
import sttp.client.testing.HttpTest

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: SttpBackend[IO, Any] = AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  override def throwsExceptionOnUnsupportedEncoding = false
}
