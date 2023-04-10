package sttp.client4.http4s

import cats.effect.IO
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client4.StreamBackend
import sttp.client4.impl.fs2.Fs2StreamingTest

import scala.concurrent.ExecutionContext
import sttp.capabilities.fs2.Fs2Streams

class Http4sHttpStreamingTest extends Fs2StreamingTest {

  private val blazeClientBuilder = BlazeClientBuilder[IO](ExecutionContext.global)
  override val backend: StreamBackend[IO, Fs2Streams[IO]] =
    Http4sBackend.usingBlazeClientBuilder(blazeClientBuilder, blocker).allocated.unsafeRunSync()._1

}
