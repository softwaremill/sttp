package sttp.client4.httpclient.cats

import cats.effect.IO
import sttp.client4.impl.cats.CatsRetryTest
import sttp.client4.testing.HttpTest

class HttpClientCatsHttpTest extends HttpTest[IO] with CatsRetryTest with HttpClientCatsTestBase {
  override def supportsHostHeaderOverride = false

  override def supportsDeflateWrapperChecking = false
}
