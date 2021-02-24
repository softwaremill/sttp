package sttp.client3.impl.monix

import client3.impl.monix.{FetchMonixBackend, MonixStreamingTest}
import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client3.SttpBackend

class FetchMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] = FetchMonixBackend()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
