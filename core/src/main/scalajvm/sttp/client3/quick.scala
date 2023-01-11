package sttp.client3

object quick extends SttpApi {
  lazy val backend: SyncBackend = HttpClientSyncBackend()
  lazy val simpleHttpClient: SimpleHttpClient = SimpleHttpClient(backend)
}
