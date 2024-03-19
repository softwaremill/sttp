package sttp.client3.asynchttpclient

import sttp.client3.testing.HttpTest

abstract class AsyncHttpClientHttpTest[F[_]] extends HttpTest[F] {
  override protected def throwsExceptionOnUnsupportedEncoding = false
  override protected def supportsAutoDecompressionDisabling = false
  override protected def supportsNonAsciiHeaderValues = false
}
