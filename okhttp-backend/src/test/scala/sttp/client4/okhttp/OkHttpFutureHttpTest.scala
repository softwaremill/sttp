package sttp.client4.okhttp

import sttp.client4.WebSocketBackend
import sttp.client4.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class OkHttpFutureHttpTest extends OkHttpHttpTest[Future] {

  override val backend: WebSocketBackend[Future] = OkHttpFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
