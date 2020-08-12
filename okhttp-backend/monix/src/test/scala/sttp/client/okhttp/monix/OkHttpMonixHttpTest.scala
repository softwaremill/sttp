package sttp.client.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client.SttpBackend
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}

class OkHttpMonixHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, MonixStreams] = OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
