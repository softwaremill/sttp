package sttp.client4.asynchttpclient.future

import sttp.client4.Backend
import sttp.client4.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client4.testing.ConvertToFuture

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends AsyncHttpClientHttpTest[Future] {

  override val backend: Backend[Future] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
  override def supportsResponseAsInputStream = false
}
