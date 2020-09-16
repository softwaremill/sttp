package sttp.client3.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client3.SttpBackend
import sttp.client3.impl.monix.MonixStreamingTest

class AsyncHttpClientMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
