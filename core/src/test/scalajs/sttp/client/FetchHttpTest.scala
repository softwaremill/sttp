package sttp.client

import sttp.client.testing.{AbstractFetchHttpTest, ConvertToFuture}

import scala.concurrent.Future

class FetchHttpTest extends AbstractFetchHttpTest[Future, Nothing] {

  override val backend: SttpBackend[Future, Any] = FetchBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override protected def supportsCustomMultipartContentType = false
}
