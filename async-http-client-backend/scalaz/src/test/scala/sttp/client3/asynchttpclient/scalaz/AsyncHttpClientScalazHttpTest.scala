package sttp.client3.asynchttpclient.scalaz

import scalaz.concurrent.Task
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.AsyncHttpClientHttpTest
import sttp.client3.impl.scalaz.convertScalazTaskToFuture
import sttp.client3.testing.ConvertToFuture

class AsyncHttpClientScalazHttpTest extends AsyncHttpClientHttpTest[Task] {

  override val backend: SttpBackend[Task, Any] = AsyncHttpClientScalazBackend().unsafePerformSync
  override implicit val convertToFuture: ConvertToFuture[Task] = convertScalazTaskToFuture

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: Task[T], timeoutMillis: Int): Task[Option[T]] = t.map(Some(_))
}
