package sttp.client3.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.capabilities.monix.MonixStreams
import sttp.client3.SttpBackend
import sttp.client3.impl.monix.convertMonixTaskToFuture
import sttp.client3.testing.{ConvertToFuture, HttpTest}

class OkHttpMonixHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, MonixStreams] = OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
