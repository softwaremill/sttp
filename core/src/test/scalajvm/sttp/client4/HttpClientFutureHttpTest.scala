package sttp.client4

import sttp.client4.httpclient.HttpClientFutureBackend
import sttp.client4.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class HttpClientFutureHttpTest extends HttpTest[Future] {
  override val backend: Backend[Future] = HttpClientFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
  override def supportsCancellation: Boolean = false
  override def supportsDeflateWrapperChecking = false

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
