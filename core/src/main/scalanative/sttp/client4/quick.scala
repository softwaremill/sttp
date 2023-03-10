package sttp.client4

object quick extends SttpApi {
  lazy val backend: SyncBackend = CurlBackend()
  lazy val simpleHttpClient: SimpleHttpClient = SimpleHttpClient(backend)
}
