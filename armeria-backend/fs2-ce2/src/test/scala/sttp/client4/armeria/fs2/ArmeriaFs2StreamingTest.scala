package sttp.client4.armeria.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.{BackendOptions, StreamBackend}
import sttp.client4.armeria.ArmeriaWebClient
import sttp.client4.impl.fs2.Fs2StreamingTest
import sttp.client4.testing.RetryTests

import java.time.Duration

// streaming tests often fail with a ClosedSessionException, see https://github.com/line/armeria/issues/1754
class ArmeriaFs2StreamingTest extends Fs2StreamingTest with RetryTests {
  override val backend: StreamBackend[IO, Fs2Streams[IO]] =
    ArmeriaFs2Backend.usingClient(
      // the default caused timeouts in SSE tests
      ArmeriaWebClient.newClient(BackendOptions.Default, _.writeTimeout(Duration.ofMillis(0)))
    )

  override protected def supportsStreamingMultipartParts: Boolean = false
}
