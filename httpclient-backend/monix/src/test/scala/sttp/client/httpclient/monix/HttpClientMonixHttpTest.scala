package sttp.client.httpclient.monix

import monix.eval.Task
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.SttpBackend
import monix.execution.Scheduler.Implicits.global

class HttpClientMonixHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, Any] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
