package com.softwaremill.sttp.okhttp.monix

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import monix.eval.Task

class OkHttpMonixHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Nothing] =
    OkHttpMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] =
    com.softwaremill.sttp.impl.monix.convertToFuture
}
