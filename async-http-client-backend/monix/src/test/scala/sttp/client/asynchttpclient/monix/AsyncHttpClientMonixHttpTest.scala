package sttp.client.asynchttpclient.monix

import monix.eval.Task
import sttp.client.SttpBackend
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientMonixHttpTest extends HttpTest[Task] {

  import monix.execution.Scheduler.Implicits.global

  override implicit val backend: SttpBackend[Task, Nothing] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

}
