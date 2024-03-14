package sttp.client4.httpclient.fs2

import cats.effect.IO
import sttp.client4.testing.HttpTest

class HttpClientFs2HttpTest extends HttpTest[IO] with HttpClientFs2TestBase {
  override def supportsHostHeaderOverride = false
  override def supportsResponseAsInputStream = false
}
