package sttp.client4.asynchttpclient.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.impl.cats.TestIODispatcher
import sttp.client4.impl.fs2.Fs2StreamingTest

class AsyncHttpClientFs2HttpStreamingTest extends Fs2StreamingTest with TestIODispatcher {
  override val backend: StreamBackend[IO, Fs2Streams[IO]] = AsyncHttpClientFs2Backend[IO](dispatcher).unsafeRunSync()
  override protected def supportsStreamingMultipartParts: Boolean = false
}
