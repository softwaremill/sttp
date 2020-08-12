package sttp.client.okhttp

import sttp.capabilities.WebSockets
import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{Identity, SttpBackend}

class OkHttpSyncHttpTest extends HttpTest[Identity] {
  override val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()

  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
