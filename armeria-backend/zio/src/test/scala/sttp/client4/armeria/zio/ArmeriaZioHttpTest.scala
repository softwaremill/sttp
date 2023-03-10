package sttp.client4.armeria.zio

import sttp.client4._
import sttp.client4.impl.zio.ZioTestBase
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import zio.Task

class ArmeriaZioHttpTest extends HttpTest[Task] with ZioTestBase {

  override val backend: Backend[Task] = unsafeRunSyncOrThrow(ArmeriaZioBackend())
  override implicit val convertToFuture: ConvertToFuture[Task] = convertZioTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
}
