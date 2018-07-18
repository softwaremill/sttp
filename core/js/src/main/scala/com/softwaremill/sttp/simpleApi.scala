package com.softwaremill.sttp

object simpleApi extends SttpApi{
  implicit lazy val backend = FetchBackend()
}