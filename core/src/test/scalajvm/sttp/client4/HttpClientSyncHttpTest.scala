package sttp.client4

import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.testing.{ConvertToFuture, HttpTest}

class HttpClientSyncHttpTest extends HttpTest[Identity] {
  override val backend: WebSocketSyncBackend = HttpClientSyncBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsHostHeaderOverride = false
  override def supportsCancellation: Boolean = false
  override def supportsDeflateWrapperChecking = false

  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
}
