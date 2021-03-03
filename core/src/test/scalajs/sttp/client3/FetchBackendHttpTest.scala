package sttp.client3

import sttp.client3.testing.{AbstractFetchHttpTest, ConvertToFuture}

import scala.concurrent.Future

class FetchBackendHttpTest extends AbstractFetchHttpTest[Future, Nothing] {

  override val backend: SttpBackend[Future, Any] = FetchBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override protected def supportsCustomMultipartContentType = false

  override protected def supportsCustomMultipartEncoding = false
}
