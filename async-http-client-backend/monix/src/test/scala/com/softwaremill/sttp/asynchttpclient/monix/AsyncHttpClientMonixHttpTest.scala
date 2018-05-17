package com.softwaremill.sttp.asynchttpclient.monix

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import _root_.monix.eval.Task

class AsyncHttpClientMonixHttpTest extends HttpTest[Task] {

  import _root_.monix.execution.Scheduler.Implicits.global

  override implicit val backend: SttpBackend[Task, Nothing] =
    AsyncHttpClientMonixBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] =
    com.softwaremill.sttp.impl.monix.convertToFuture
}
