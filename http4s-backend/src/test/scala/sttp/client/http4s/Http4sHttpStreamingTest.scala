package sttp.client.http4s

import cats.effect.IO
import sttp.client.SttpBackend
import sttp.client.impl.fs2.Fs2StreamingTest

import scala.concurrent.ExecutionContext
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.capabilities.fs2.Fs2Streams

class Http4sHttpStreamingTest extends Fs2StreamingTest {

  private val blazeClientBuilder = BlazeClientBuilder[IO](ExecutionContext.global)
  override val backend: SttpBackend[IO, Fs2Streams[IO]] =
    Http4sBackend.usingClientBuilder(blazeClientBuilder, blocker).allocated.unsafeRunSync()._1

}
