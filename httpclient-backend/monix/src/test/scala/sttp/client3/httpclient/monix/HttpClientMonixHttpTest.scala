package sttp.client3.httpclient.monix

import monix.eval.Task
import sttp.client3.impl.monix.convertMonixTaskToFuture
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import sttp.client3.SttpBackend
import monix.execution.Scheduler.Implicits.global

class HttpClientMonixHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, Any] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
