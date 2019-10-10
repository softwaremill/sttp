package sttp.client.asynchttpclient.monix

import monix.eval.Task
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}
import monix.execution.Scheduler.Implicits.global

class AsyncHttpClientMonixHttpTest extends HttpTest[Task] {
  override implicit val backend: SttpBackend[Task, Nothing, NothingT] = AsyncHttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
