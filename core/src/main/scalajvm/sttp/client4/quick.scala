package sttp.client4

object quick extends SttpApi {
  lazy val backend: SyncBackend = HttpClientSyncBackend()
}
