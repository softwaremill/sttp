package sttp.client4.impl.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client4.StreamBackend
import sttp.client4.httpclient.monix.HttpClientMonixBackend

class HttpClientMonixStreamingTest extends MonixStreamingTest {
  override val backend: StreamBackend[Task, MonixStreams] =
    HttpClientMonixBackend().runSyncUnsafe()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
