package sttp.client.okhttp

import sttp.client._

object quick extends SttpApi {
  lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
}
