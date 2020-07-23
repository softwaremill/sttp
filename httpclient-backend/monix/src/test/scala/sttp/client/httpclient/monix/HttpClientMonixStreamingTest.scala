package sttp.client.httpclient.monix

import monix.eval.Task
import sttp.client.impl.monix.{MonixStreamingTest, MonixStreams}
import sttp.client.{NothingT, SttpBackend}
import monix.execution.Scheduler.Implicits.global

class HttpClientMonixStreamingTest extends MonixStreamingTest {

  override implicit val backend: SttpBackend[Task, MonixStreams, NothingT] =
    HttpClientMonixBackend().runSyncUnsafe()
}
