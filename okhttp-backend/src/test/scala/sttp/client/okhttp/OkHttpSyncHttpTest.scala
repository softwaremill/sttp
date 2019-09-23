package sttp.client.okhttp

import sttp.client.{Identity, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

class OkHttpSyncHttpTest extends HttpTest[Identity] {

  override implicit val backend: SttpBackend[Identity, Nothing] = OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
