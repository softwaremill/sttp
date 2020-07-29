package sttp.client

object quick extends SttpApi {
  implicit lazy val backend: SttpBackend[Identity, Any] = CurlBackend()
}
