package sttp.client4.akkahttp

import sttp.client4.Backend
import sttp.client4.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class AkkaHttpClientHttpTest extends HttpTest[Future] {
  override val backend: Backend[Future] = AkkaHttpBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
