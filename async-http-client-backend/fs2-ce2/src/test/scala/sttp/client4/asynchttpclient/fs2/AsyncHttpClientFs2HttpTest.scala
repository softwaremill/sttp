package sttp.client4.asynchttpclient.fs2

import cats.effect.{Blocker, IO}
import sttp.client4.Backend
import sttp.client4.impl.cats.CatsTestBase
import sttp.client4.testing.HttpTest

import scala.concurrent.ExecutionContext.global

class AsyncHttpClientFs2HttpTest extends HttpTest[IO] with CatsTestBase {
  override val backend: Backend[IO] =
    AsyncHttpClientFs2Backend[IO](Blocker.liftExecutionContext(global)).unsafeRunSync()

  override def throwsExceptionOnUnsupportedEncoding = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsResponseAsInputStream = false
}
