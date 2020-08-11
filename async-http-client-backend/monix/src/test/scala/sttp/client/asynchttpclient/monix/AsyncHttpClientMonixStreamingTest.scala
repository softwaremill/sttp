package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client.SttpBackend
import sttp.client.impl.monix.{MonixStreamingTest, MonixStreams}

class AsyncHttpClientMonixStreamingTest extends MonixStreamingTest {
  override implicit val backend: SttpBackend[Task, MonixStreams] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
}
