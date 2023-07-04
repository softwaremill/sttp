package sttp.client4.armeria.scalaz

import scalaz.concurrent.Task
import sttp.client4._
import sttp.client4.impl.scalaz.convertScalazTaskToFuture
import sttp.client4.testing.{ConvertToFuture, HttpTest}

class ArmeriaScalazHttpTest extends HttpTest[Task] {

  override val backend: Backend[Task] = ArmeriaScalazBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertScalazTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsAutoDecompressionDisabling = false
  override def supportsDeflateWrapperChecking = false // armeria hangs
  override def supportsEmptyContentEncoding = false

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] = t.map(Some(_))
}
