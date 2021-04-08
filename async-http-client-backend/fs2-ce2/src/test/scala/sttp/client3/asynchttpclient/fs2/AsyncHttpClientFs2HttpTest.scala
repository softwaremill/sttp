package sttp.client3.asynchttpclient.fs2

import cats.effect.{Blocker, IO}
import sttp.client3.SttpBackend
import sttp.client3.impl.cats.CatsTestBase
import sttp.client3.testing.HttpTest

import scala.concurrent.ExecutionContext.global

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: SttpBackend[IO, Any] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  override def throwsExceptionOnUnsupportedEncoding = false
}
