package sttp.client4.armeria.fs2

import cats.effect.IO
import sttp.client4.Backend
import sttp.client4.impl.cats.{CatsTestBase, TestIODispatcher}
import sttp.client4.testing.HttpTest

class ArmeriaFs2HttpTest extends HttpTest[IO] with CatsTestBase with TestIODispatcher {
  override val backend: Backend[IO] = ArmeriaFs2Backend(dispatcher = dispatcher)

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
  override def supportsEmptyContentEncoding = false
  override def supportsResponseAsInputStream = false
}
