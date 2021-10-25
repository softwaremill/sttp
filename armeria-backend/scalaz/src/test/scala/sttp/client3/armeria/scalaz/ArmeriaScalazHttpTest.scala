package sttp.client3.armeria.scalaz

import scalaz.concurrent.Task
import sttp.client3._
import sttp.client3.impl.scalaz.convertScalazTaskToFuture
import sttp.client3.testing.{ConvertToFuture, HttpTest}

class ArmeriaScalazHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, Any] = ArmeriaScalazBackend()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertScalazTaskToFuture

  override def supportsHostHeaderOverride = false
  override def supportAutoDecompressionDisabling = false
  override def supportsCancellation = false

  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] = t.map(Some(_))
}
