package sttp.client.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.monix.{MonixStreams, convertMonixTaskToFuture}
import sttp.client.testing.{ConvertToFuture, HttpTest}

class OkHttpMonixHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, MonixStreams, NothingT] = OkHttpMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture
}
