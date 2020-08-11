package sttp.client

object quick extends SttpApi {
  lazy val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
}
