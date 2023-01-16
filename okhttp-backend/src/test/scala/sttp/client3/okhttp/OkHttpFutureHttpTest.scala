package sttp.client3.okhttp

import sttp.client3.WebSocketBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class OkHttpFutureHttpTest extends HttpTest[Future] {

  override val backend: WebSocketBackend[Future] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
  override def supportsDeflateWrapperChecking = false
}
