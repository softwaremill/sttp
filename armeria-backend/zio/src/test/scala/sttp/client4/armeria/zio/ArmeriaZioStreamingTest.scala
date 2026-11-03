package sttp.client4.armeria.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.armeria.ArmeriaWebClient
import sttp.client4.{BackendOptions, StreamBackend}
import sttp.client4.impl.zio.ZioStreamingTest
import sttp.client4.testing.RetryTests
import zio.Task

import java.time.Duration

// streaming tests often fail with a ClosedSessionException, see https://github.com/line/armeria/issues/1754
class ArmeriaZioStreamingTest extends ZioStreamingTest with RetryTests {
  override val backend: StreamBackend[Task, ZioStreams] =
    unsafeRunSyncOrThrow(
      ArmeriaZioBackend.usingClient(
        // the default caused timeouts in SSE tests
        ArmeriaWebClient.newClient(BackendOptions.Default, _.writeTimeout(Duration.ofMillis(0)))
      )
    )

  override protected def supportsStreamingMultipartParts: Boolean = false
}
