package sttp.client.httpclient.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import sttp.client.impl.monix.MonixStreamingTest
import sttp.client.{NothingT, SttpBackend}
import monix.execution.Scheduler.Implicits.global

class HttpClientMonixStreamingTest extends MonixStreamingTest {

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer], NothingT] =
    HttpClientMonixBackend().runSyncUnsafe()
}
