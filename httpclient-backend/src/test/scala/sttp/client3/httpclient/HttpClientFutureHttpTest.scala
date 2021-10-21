package sttp.client3.httpclient

import sttp.client3.testing.{ConvertToFuture, HttpTest}
import sttp.client3.SttpBackend

import scala.concurrent.Future

class HttpClientFutureHttpTest extends HttpTest[Future] {
  override val backend: SttpBackend[Future, Any] = HttpClientFutureBackend(disableAutoDecompression = true)
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
