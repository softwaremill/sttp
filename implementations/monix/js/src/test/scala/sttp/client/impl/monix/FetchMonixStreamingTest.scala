package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import sttp.client.SttpBackend

class FetchMonixStreamingTest extends MonixStreamingTest {

  override protected def endpoint: String = "localhost:51823"

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] = FetchMonixBackend()

}
