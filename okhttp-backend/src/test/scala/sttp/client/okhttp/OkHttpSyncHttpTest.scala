package sttp.client.okhttp

import sttp.client.{Identity, NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}

class OkHttpSyncHttpTest extends HttpTest[Identity] {

  override implicit val backend: SttpBackend[Identity, Nothing, NothingT] = OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
