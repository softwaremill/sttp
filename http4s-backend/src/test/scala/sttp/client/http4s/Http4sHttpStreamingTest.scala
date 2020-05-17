package sttp.client.http4s

import cats.effect.IO
import fs2.Stream
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.fs2.Fs2StreamingTest

import scala.concurrent.ExecutionContext

import org.http4s.client.blaze.BlazeClientBuilder

class Http4sHttpStreamingTest extends Fs2StreamingTest {

  private val blazeClientBuilder = BlazeClientBuilder[IO](ExecutionContext.global)
  override implicit val backend: SttpBackend[IO, Stream[IO, Byte], NothingT] =
    Http4sBackend.usingClientBuilder(blazeClientBuilder, blocker).allocated.unsafeRunSync()._1

}
