package sttp.client3

object quick extends SttpApi {
  lazy val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
  lazy val simpleHttpClient: SimpleHttpClient = SimpleHttpClient(backend)
}
