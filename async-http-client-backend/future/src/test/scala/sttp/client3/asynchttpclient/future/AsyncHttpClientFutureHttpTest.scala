package sttp.client3.asynchttpclient.future

import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client3.testing.ConvertToFuture

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends AsyncHttpClientHttpTest[Future] {

  override val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
