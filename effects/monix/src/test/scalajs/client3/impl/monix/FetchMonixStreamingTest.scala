package sttp.client3.impl.monix

import monix.eval.Task
import sttp.client3.SttpBackend
import sttp.capabilities.monix.MonixStreams

class FetchMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] = FetchMonixBackend()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
