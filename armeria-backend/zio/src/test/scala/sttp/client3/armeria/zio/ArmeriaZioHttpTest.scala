package sttp.client3.armeria.zio

import sttp.client3._
import sttp.client3.impl.zio.ZioTestBase
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import zio.Task

class ArmeriaZioHttpTest extends HttpTest[Task] with ZioTestBase {

  override val backend: SttpBackend[Task, Any] = unsafeRunSyncOrThrow(ArmeriaZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
}
