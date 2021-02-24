package sttp.client3.impl.monix

import monix.eval.Task
import sttp.capabilities.monix.MonixStreams
import sttp.client3.SttpBackend
import sttp.client3.testing.{AbstractFetchHttpTest, ConvertToFuture}

class FetchMonixHttpTest extends AbstractFetchHttpTest[Task, MonixStreams] {

  override val backend: SttpBackend[Task, MonixStreams] = FetchMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override protected def supportsCustomMultipartContentType = false
}
