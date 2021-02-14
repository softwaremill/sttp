package sttp.client3.armeria.zio

import sttp.client3._
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import zio.Task

class ArmeriaZioHttpTest extends HttpTest[Task] with ZioTestBase {

  // FIXME(ikhoon): A request failed with `ResponseTimeoutException`.
  //                However, "read exceptions - timeout" test never ends.

  override val backend: SttpBackend[Task, Any] = runtime.unsafeRun(ArmeriaZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsMultipart = false
  override def supportsCancellation = false
}
