package sttp.client.http4s

import cats.effect.IO
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.SttpBackend
import sttp.client.impl.cats.CatsTestBase
import sttp.client.testing.HttpTest

import scala.concurrent.ExecutionContext

class Http4sHttpTest extends HttpTest[IO] with CatsTestBase {
  private val blazeClientBuilder = BlazeClientBuilder[IO](ExecutionContext.global)

  override val backend: SttpBackend[IO, Any] =
    Http4sBackend.usingClientBuilder(blazeClientBuilder, blocker).allocated.unsafeRunSync()._1

  override protected def supportsRequestTimeout = false
  override protected def supportsCustomMultipartContentType = false
}
