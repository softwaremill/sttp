package sttp.client3.http4s

import cats.effect.IO
import sttp.client3.SttpBackend
import sttp.client3.impl.fs2.Fs2StreamingTest

import scala.concurrent.ExecutionContext
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.capabilities.fs2.Fs2Streams

class Http4sHttpStreamingTest extends Fs2StreamingTest {

  private val blazeClientBuilder = BlazeClientBuilder[IO]
  override val backend: SttpBackend[IO, Fs2Streams[IO]] =
    blazeClientBuilder.resource.map(c => Http4sBackend.usingClient(c)).allocated.unsafeRunSync()._1

}
