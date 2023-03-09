package sttp.client3.okhttp

import sttp.client3.testing.{ConvertToFuture, HttpTest}
import sttp.client3.{Identity, WebSocketBackend}

class OkHttpSyncHttpTest extends HttpTest[Identity] {
  override val backend: WebSocketBackend[Identity] = OkHttpSyncBackend()

  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
  override def supportsDeflateWrapperChecking = false
}
