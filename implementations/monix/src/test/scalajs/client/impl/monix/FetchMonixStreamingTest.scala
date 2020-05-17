package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import sttp.client.{NothingT, SttpBackend}

class FetchMonixStreamingTest extends MonixStreamingTest {
  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer], NothingT] = FetchMonixBackend()
}
