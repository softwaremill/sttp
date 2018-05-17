package com.softwaremill.sttp.okhttp

import com.softwaremill.sttp.{Id, SttpBackend}
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

class OkHttpSyncHttpTest extends HttpTest[Id] {

  override implicit val backend: SttpBackend[Id, Nothing] =
    OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Id] =
    ConvertToFuture.id
}
