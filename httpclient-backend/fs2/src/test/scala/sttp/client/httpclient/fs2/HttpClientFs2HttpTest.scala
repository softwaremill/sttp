package sttp.client.httpclient.fs2

import cats.effect.IO
import sttp.client.testing.HttpTest

class HttpClientFs2HttpTest extends HttpTest[IO] with HttpClientFs2TestBase
