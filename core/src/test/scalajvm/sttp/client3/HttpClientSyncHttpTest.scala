package sttp.client3

import sttp.client3.testing.{ConvertToFuture, HttpTest}

class HttpClientSyncHttpTest extends HttpTest[Identity] {
  override val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsHostHeaderOverride = false
  override def supportsCancellation: Boolean = false

  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
}
