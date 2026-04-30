package sttp.client4.http4s

import cats.effect.IO
import sttp.client4.StreamBackend
import sttp.client4.impl.fs2.Fs2StreamingTest
import sttp.capabilities.fs2.Fs2Streams

class Http4sNativeHttpStreamingTest extends Fs2StreamingTest {

  override val backend: StreamBackend[IO, Fs2Streams[IO]] =
    Http4sBackend.usingDefaultEmberClientBuilder[IO]().allocated.unsafeRunSync()._1
}
