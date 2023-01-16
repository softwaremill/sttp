package sttp.client3.asynchttpclient.future

import sttp.client3.Backend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AsyncHttpClientFutureHttpTest extends HttpTest[Future] {

  override val backend: Backend[Future] = AsyncHttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def throwsExceptionOnUnsupportedEncoding = false

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
  override def supportsAutoDecompressionDisabling = false
}
