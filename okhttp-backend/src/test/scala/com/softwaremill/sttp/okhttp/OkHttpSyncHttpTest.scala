package com.softwaremill.sttp.okhttp

import com.softwaremill.sttp.{Identity, SttpBackend}
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

class OkHttpSyncHttpTest extends HttpTest[Identity] {

  override implicit val backend: SttpBackend[Identity, Nothing] = OkHttpSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
