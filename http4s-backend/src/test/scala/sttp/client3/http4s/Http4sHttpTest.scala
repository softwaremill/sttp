package sttp.client3.http4s

import cats.effect.IO
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client3.SttpBackend
import sttp.client3.impl.cats.{CatsRetryTest, CatsTestBase}
import sttp.client3.testing.HttpTest

import scala.concurrent.ExecutionContext

class Http4sHttpTest extends HttpTest[IO] with CatsRetryTest with CatsTestBase {
  private val blazeClientBuilder = BlazeClientBuilder[IO]

  override val backend: SttpBackend[IO, Any] =
    blazeClientBuilder.resource.map(c => Http4sBackend.usingClient(c)).allocated.unsafeRunSync()._1

  override protected def supportsRequestTimeout = false
  override protected def supportsCustomMultipartContentType = false
}
