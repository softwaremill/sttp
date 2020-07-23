package sttp.client.impl.monix

import monix.eval.Task
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.monix.MonixStreams

class FetchMonixStreamingTest extends MonixStreamingTest {
  override implicit val backend: SttpBackend[Task, MonixStreams, NothingT] = FetchMonixBackend()
}
