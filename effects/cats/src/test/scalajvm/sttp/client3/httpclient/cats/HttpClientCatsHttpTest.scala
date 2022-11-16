package sttp.client3.httpclient.cats

import cats.effect.IO
import sttp.client3.testing.HttpTest

class HttpClientCatsHttpTest extends HttpTest[IO] with HttpClientCatsTestBase {
  override def supportsHostHeaderOverride = false

  override def supportsDeflateWrapperChecking = false
}
