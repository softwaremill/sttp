package sttp.client3.okhttp

import sttp.capabilities.WebSockets
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import sttp.client3.{Identity, SttpBackend}

class OkHttpSyncHttpTest extends HttpTest[Identity] {
  override val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()

  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
