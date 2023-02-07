package sttp.client3.armeria.monix

import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client3.{SttpBackend, SttpBackendOptions}
import sttp.client3.impl.monix.MonixStreamingTest
import monix.execution.Scheduler.Implicits.global
import sttp.client3.armeria.ArmeriaWebClient

import java.time.Duration

class ArmeriaMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] =
    ArmeriaMonixBackend.usingClient(
      // the default caused timeouts in SSE tests
      ArmeriaWebClient.newClient(SttpBackendOptions.Default, _.writeTimeout(Duration.ofMillis(0)))
    )

  override protected def supportsStreamingMultipartParts: Boolean = false
}
