package sttp.client3.asynchttpclient.fs2

import cats.effect.IO
import sttp.client3.SttpBackend
import sttp.client3.impl.cats.{CatsTestBase, DispatcherIOMixin}
import sttp.client3.testing.HttpTest

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] with DispatcherIOMixin with CatsTestBase {
  override val backend: SttpBackend[IO, Any] =
    AsyncHttpClientFs2Backend[IO]().unsafeRunSync()

  override def throwsExceptionOnUnsupportedEncoding = false
}
