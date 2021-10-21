package sttp.client3.httpclient

import sttp.client3._

object quick extends SttpApi {
  lazy val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend(disableAutoDecompression = false)
}
