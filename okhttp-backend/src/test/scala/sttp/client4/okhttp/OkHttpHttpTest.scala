package sttp.client4.okhttp

import sttp.client4.testing.HttpTest

abstract class OkHttpHttpTest[F[_]] extends HttpTest[F] {
  override protected def supportsDeflateWrapperChecking = false
  override protected def supportsNonAsciiHeaderValues = false
}
