package sttp.client3

object quick extends SttpApi {
  lazy val httpUrlConnectionBackend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  lazy val httpClientBackend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
}
