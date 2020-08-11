package sttp.client.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client.SttpBackend
import sttp.client.impl.monix.{MonixStreamingTest, MonixStreams}

class OkHttpMonixStreamingTest extends MonixStreamingTest {
  override implicit val backend: SttpBackend[Task, MonixStreams] =
    OkHttpMonixBackend().runSyncUnsafe()
}
