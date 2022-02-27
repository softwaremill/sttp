package sttp.client3.armeria.future

import sttp.client3.SttpBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class ArmeriaFutureHttpTest extends HttpTest[Future] {

  override val backend: SttpBackend[Future, Any] = ArmeriaFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
