package sttp.client3.asynchttpclient.scalaz

import scalaz.concurrent.Task
import sttp.client3.SttpBackend
import sttp.client3.impl.scalaz.convertScalazTaskToFuture
import sttp.client3.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientScalazHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, Any] = AsyncHttpClientScalazBackend().unsafePerformSync
  override implicit val convertToFuture: ConvertToFuture[Task] = convertScalazTaskToFuture

  override def throwsExceptionOnUnsupportedEncoding = false
}
