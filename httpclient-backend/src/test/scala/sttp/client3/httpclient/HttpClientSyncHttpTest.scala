package sttp.client3.httpclient

import sttp.client3.testing.{ConvertToFuture, HttpTest}
import sttp.client3.{Identity, SttpBackend}

class HttpClientSyncHttpTest extends HttpTest[Identity] {
  override val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
