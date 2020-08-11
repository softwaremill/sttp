package sttp.client.impl.monix

import monix.eval.Task
import sttp.client.SttpBackend
import sttp.client.testing.{AbstractFetchHttpTest, ConvertToFuture}
import sttp.client.impl.monix.MonixStreams

class FetchMonixHttpTest extends AbstractFetchHttpTest[Task, MonixStreams] {

  override implicit val backend: SttpBackend[Task, MonixStreams] = FetchMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  override protected def supportsCustomMultipartContentType = false
}
