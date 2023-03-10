package sttp.client4.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client4.StreamBackend
import sttp.client4.impl.monix.MonixStreamingTest

class OkHttpMonixStreamingTest extends MonixStreamingTest {
  override val backend: StreamBackend[Task, MonixStreams] =
    OkHttpMonixBackend().runSyncUnsafe()
}
