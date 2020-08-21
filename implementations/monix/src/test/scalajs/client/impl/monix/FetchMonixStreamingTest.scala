package sttp.client.impl.monix

import monix.eval.Task
import sttp.client.SttpBackend
import sttp.capabilities.monix.MonixStreams

class FetchMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] = FetchMonixBackend()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
