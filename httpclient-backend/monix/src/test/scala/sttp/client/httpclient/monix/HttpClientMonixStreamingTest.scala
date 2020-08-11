package sttp.client.httpclient.monix

import monix.eval.Task
import sttp.client.impl.monix.{MonixStreamingTest, MonixStreams}
import sttp.client.SttpBackend
import monix.execution.Scheduler.Implicits.global

class HttpClientMonixStreamingTest extends MonixStreamingTest {
  override val backend: SttpBackend[Task, MonixStreams] =
    HttpClientMonixBackend().runSyncUnsafe()
}
