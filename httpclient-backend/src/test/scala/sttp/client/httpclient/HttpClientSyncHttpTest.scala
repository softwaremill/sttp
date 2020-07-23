package sttp.client.httpclient

import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{Identity, NothingT, SttpBackend}

class HttpClientSyncHttpTest extends HttpTest[Identity] {

  override implicit val backend: SttpBackend[Identity, Any, NothingT] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
