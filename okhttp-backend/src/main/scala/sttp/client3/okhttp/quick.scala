package sttp.client3.okhttp

import sttp.client3._

object quick extends SttpApi {
  lazy val backend: WebSocketBackend[Identity] = OkHttpSyncBackend()
}
