package sttp.client3.okhttp

import sttp.capabilities.WebSockets
import sttp.client3._

object quick extends SttpApi {
  lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
}
