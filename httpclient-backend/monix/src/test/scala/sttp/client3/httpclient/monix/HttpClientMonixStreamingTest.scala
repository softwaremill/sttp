package sttp.client3.httpclient.monix

import monix.eval.Task
import sttp.client3.impl.monix.MonixStreamingTest
import sttp.client3.SttpBackend
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams

class HttpClientMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] =
    HttpClientMonixBackend().runSyncUnsafe()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
