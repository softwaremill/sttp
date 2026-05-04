package sttp.client4.http4s

import cats.effect.IO
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client4.Backend
import sttp.client4.impl.cats.{CatsRetryTest, CatsTestBase}
import sttp.client4.testing.HttpTest

import scala.concurrent.ExecutionContext

class Http4sHttpTest extends HttpTest[IO] with CatsRetryTest with CatsTestBase {
  private val blazeClientBuilder = BlazeClientBuilder[IO]

  override val backend: Backend[IO] =
    Http4sBackend.usingBlazeClientBuilder(blazeClientBuilder).allocated.unsafeRunSync()._1

  override protected def supportsRequestTimeout = false
  override protected def supportsCustomMultipartContentType = false
  override protected def supportsResponseAsInputStream = false
}
