package sttp.client.httpclient

import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{Identity, SttpBackend}

class HttpClientSyncHttpTest extends HttpTest[Identity] {
  override implicit val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
