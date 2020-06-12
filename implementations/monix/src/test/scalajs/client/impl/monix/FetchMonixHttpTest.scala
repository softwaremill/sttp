package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import sttp.client.{NothingT, SttpBackend}
import sttp.client.testing.{AbstractFetchHttpTest, ConvertToFuture}

class FetchMonixHttpTest extends AbstractFetchHttpTest[Task, Observable[ByteBuffer]] {

  override implicit val backend: SttpBackend[Task, Observable[ByteBuffer], NothingT] = FetchMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override protected def supportsCustomMultipartContentType = false
}
