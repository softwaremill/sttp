package sttp.client.okhttp

import sttp.capabilities.WebSockets
import sttp.client._

object quick extends SttpApi {
  lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
}
