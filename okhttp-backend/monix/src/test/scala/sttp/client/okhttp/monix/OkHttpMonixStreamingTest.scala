package sttp.client.okhttp.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.monix.MonixStreamingTest

class OkHttpMonixStreamingTest extends MonixStreamingTest {

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer], NothingT] =
    OkHttpMonixBackend().runSyncUnsafe()
}
