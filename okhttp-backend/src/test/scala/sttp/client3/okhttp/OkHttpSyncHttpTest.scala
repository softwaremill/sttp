package sttp.client3.okhttp

import sttp.capabilities.WebSockets
import sttp.client3.testing.ConvertToFuture
import sttp.client3.{Identity, SttpBackend}

class OkHttpSyncHttpTest extends OkHttpHttpTest[Identity] {
  override val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()

  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
  override def supportsDeflateWrapperChecking = false
}
