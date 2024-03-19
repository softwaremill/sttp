package sttp.client3.okhttp

import sttp.client3.testing.HttpTest

abstract class OkHttpHttpTest[F[_]] extends HttpTest[F] {
  override protected def supportsDeflateWrapperChecking = false
  override protected def supportsNonAsciiHeaderValues = false
}
