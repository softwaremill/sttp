package sttp.client4.okhttp

import sttp.client4._

object quick extends SttpApi {
  lazy val backend: WebSocketSyncBackend = OkHttpSyncBackend()
}
