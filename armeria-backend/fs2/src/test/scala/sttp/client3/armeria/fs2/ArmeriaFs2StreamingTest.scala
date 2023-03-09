package sttp.client3.armeria.fs2

import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.{StreamBackend, BackendOptions}
import sttp.client3.armeria.ArmeriaWebClient
import sttp.client3.impl.cats.TestIODispatcher
import sttp.client3.impl.fs2.Fs2StreamingTest
import sttp.client3.testing.RetryTests

import java.time.Duration

// streaming tests often fail with a ClosedSessionException, see https://github.com/line/armeria/issues/1754
class ArmeriaFs2StreamingTest extends Fs2StreamingTest with TestIODispatcher with RetryTests {
  override val backend: StreamBackend[IO, Fs2Streams[IO]] =
    ArmeriaFs2Backend.usingClient(
      // the default caused timeouts in SSE tests
      ArmeriaWebClient.newClient(BackendOptions.Default, _.writeTimeout(Duration.ofMillis(0))),
      dispatcher
    )

  override protected def supportsStreamingMultipartParts: Boolean = false
}
