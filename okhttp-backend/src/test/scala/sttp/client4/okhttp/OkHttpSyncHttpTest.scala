package sttp.client4.okhttp

import sttp.client4.testing.ConvertToFuture
import sttp.client4.WebSocketBackend
import sttp.shared.Identity

class OkHttpSyncHttpTest extends OkHttpHttpTest[Identity] {
  override val backend: WebSocketBackend[Identity] = OkHttpSyncBackend()

  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
}
