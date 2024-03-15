package sttp.client4.asynchttpclient

import sttp.client4.testing.HttpTest

abstract class AsyncHttpClientHttpTest[F[_]] extends HttpTest[F] {
  override protected def supportsNonAsciiHeaderValues = false
}
