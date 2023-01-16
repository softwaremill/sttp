package sttp.client3.armeria.monix

import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client3.StreamBackend
import sttp.client3.impl.monix.MonixStreamingTest
import monix.execution.Scheduler.Implicits.global

class ArmeriaMonixStreamingTest extends MonixStreamingTest {
  override val backend: StreamBackend[Task, MonixStreams] =
    ArmeriaMonixBackend()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
