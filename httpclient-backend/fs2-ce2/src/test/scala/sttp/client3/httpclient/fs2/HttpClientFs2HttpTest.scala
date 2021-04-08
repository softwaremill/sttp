package sttp.client3.httpclient.fs2

import cats.effect.IO
import sttp.client3.testing.HttpTest

class HttpClientFs2HttpTest extends HttpTest[IO] with HttpClientFs2TestBase {
  override def supportsHostHeaderOverride = false
}
