package com.softwaremill.sttp.asynchttpclient.monix

import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.impl.monix.convertMonixTaskToFuture
import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}
import monix.eval.Task

class AsyncHttpClientMonixHttpTest extends HttpTest[Task] {

  import monix.execution.Scheduler.Implicits.global

  override implicit val backend: SttpBackend[Task, Nothing] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

}
