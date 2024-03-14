package sttp.client4.armeria.future

import sttp.client4.Backend
import sttp.client4.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class ArmeriaFutureHttpTest extends HttpTest[Future] {

  override val backend: Backend[Future] = ArmeriaFutureBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
  override def supportsEmptyContentEncoding = false
  override def supportsResponseAsInputStream = false

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
