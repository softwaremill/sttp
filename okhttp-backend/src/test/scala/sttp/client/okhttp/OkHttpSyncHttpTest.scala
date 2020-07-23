package sttp.client.okhttp

import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{Identity, NothingT, SttpBackend}

class OkHttpSyncHttpTest extends HttpTest[Identity] {
  override implicit val backend: SttpBackend[Identity, Any, NothingT] = OkHttpSyncBackend()

  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
