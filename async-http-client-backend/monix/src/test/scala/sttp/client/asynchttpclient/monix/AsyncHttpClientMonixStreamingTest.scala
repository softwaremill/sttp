package sttp.client.asynchttpclient.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.monix.{MonixStreamingTest, MonixStreams}

class AsyncHttpClientMonixStreamingTest extends MonixStreamingTest {
  override val streams: MonixStreams = MonixStreams

  override implicit val backend: SttpBackend[Task, MonixStreams, NothingT] =
    AsyncHttpClientMonixBackend().runSyncUnsafe()
}
