package sttp.client4

import sttp.client4.fetch.FetchBackend
import sttp.client4.testing.{AbstractFetchHttpTest, ConvertToFuture}

import scala.concurrent.Future

class FetchBackendHttpTest extends AbstractFetchHttpTest[Future, Nothing] {

  override val backend: Backend[Future] = FetchBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override protected def supportsCustomMultipartContentType = false

  override protected def supportsCustomMultipartEncoding = false
}
