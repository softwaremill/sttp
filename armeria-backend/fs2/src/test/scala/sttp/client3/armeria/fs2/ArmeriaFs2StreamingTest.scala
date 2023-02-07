package sttp.client3.armeria.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.{SttpBackend, SttpBackendOptions}
import sttp.client3.armeria.ArmeriaWebClient
import sttp.client3.impl.cats.TestIODispatcher
import sttp.client3.impl.fs2.Fs2StreamingTest

import java.time.Duration

class ArmeriaFs2StreamingTest extends Fs2StreamingTest with TestIODispatcher {
  override val backend: SttpBackend[IO, Fs2Streams[IO]] =
    ArmeriaFs2Backend.usingClient(
      // the default caused timeouts in SSE tests
      ArmeriaWebClient.newClient(SttpBackendOptions.Default, _.writeTimeout(Duration.ofMillis(0))),
      dispatcher
    )

  override protected def supportsStreamingMultipartParts: Boolean = false
}
