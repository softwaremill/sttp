package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client.SttpBackend
import sttp.client.impl.monix.MonixStreamingTest

class AsyncHttpClientMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
}
