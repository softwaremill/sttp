package sttp.client3.armeria.monix

import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client3.{StreamBackend, SttpBackendOptions}
import sttp.client3.impl.monix.MonixStreamingTest
import monix.execution.Scheduler.Implicits.global
import sttp.client3.armeria.ArmeriaWebClient
import sttp.client3.testing.RetryTests

import java.time.Duration

// streaming tests often fail with a ClosedSessionException, see https://github.com/line/armeria/issues/1754
class ArmeriaMonixStreamingTest extends MonixStreamingTest with RetryTests {
  override val backend: StreamBackend[Task, MonixStreams] =
    ArmeriaMonixBackend.usingClient(
      // the default caused timeouts in SSE tests
      ArmeriaWebClient.newClient(SttpBackendOptions.Default, _.writeTimeout(Duration.ofMillis(0)))
    )

  override protected def supportsStreamingMultipartParts: Boolean = false
}
