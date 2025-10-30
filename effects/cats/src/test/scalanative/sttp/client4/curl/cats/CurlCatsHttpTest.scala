package sttp.client4.curl.cats

import cats.effect.IO
import sttp.client4.impl.cats.CatsRetryTest
import sttp.client4.testing.HttpTest

class CurlCatsHttpTest extends HttpTest[IO] with CurlCatsTestBase with CatsRetryTest {
  override def supportsHostHeaderOverride = false
  override def supportsDeflateWrapperChecking = false
  override def supportsCancellation = false
}
