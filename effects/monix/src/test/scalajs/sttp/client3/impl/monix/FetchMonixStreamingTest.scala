package sttp.client3.impl.monix

import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client3.StreamBackend

class FetchMonixStreamingTest extends MonixStreamingTest {
  override val backend: StreamBackend[Task, MonixStreams] = FetchMonixBackend()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
