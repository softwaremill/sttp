package sttp.client3.armeria.future

import scala.concurrent.Future
import sttp.client3.SttpBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

class ArmeriaFutureHttpTest extends HttpTest[Future] {

  override val backend: SttpBackend[Future, Any] = ArmeriaFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
  override def supportsMultipart = false
  override def supportsCancellation = false

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
