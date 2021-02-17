package sttp.client3.armeria

import sttp.client3.SttpBackend
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.Future

class ArmeriaBackendTest extends HttpTest[Future] {

  override val backend: SttpBackend[Future, Any] = ArmeriaBackend()
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future

  override def supportsHostHeaderOverride = false
  override def supportsMultipart = false
  override def supportsCancellation = false

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))
}
