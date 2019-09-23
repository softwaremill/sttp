package sttp.client

object quick extends SttpApi {
  implicit lazy val backend = CurlBackend()
}
