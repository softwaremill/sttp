package sttp.client3.asynchttpclient.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.impl.cats.TestIODispatcher
import sttp.client3.impl.fs2.Fs2StreamingTest

class AsyncHttpClientFs2HttpStreamingTest extends Fs2StreamingTest with TestIODispatcher {
  override val backend: SttpBackend[IO, Fs2Streams[IO]] = AsyncHttpClientFs2Backend[IO](dispatcher).unsafeRunSync()
  override protected def supportsStreamingMultipartParts: Boolean = false
}
