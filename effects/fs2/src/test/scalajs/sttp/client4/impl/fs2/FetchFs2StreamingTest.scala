package sttp.client4.impl.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.testing.streaming.StreamingTest

class FetchFs2StreamingTest extends StreamingTest[IO, Fs2Streams[IO]] with Fs2StreamingTest {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]

  override val backend: StreamBackend[IO, Fs2Streams[IO]] = FetchFs2Backend()

  override protected def supportsStreamingMultipartParts: Boolean = false

}
