package com.softwaremill.sttp

object quick extends SttpApi {
  implicit lazy val backend = CurlBackend()
}
