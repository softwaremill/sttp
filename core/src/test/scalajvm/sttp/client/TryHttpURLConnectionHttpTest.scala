package sttp.client

import scala.util.Try

import sttp.client.testing.ConvertToFuture
import sttp.client.testing.HttpTest

class TryHttpURLConnectionHttpTest extends HttpTest[Try] {

  override val backend: SttpBackend[Try, Any] = TryHttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Try] = ConvertToFuture.scalaTry
}
