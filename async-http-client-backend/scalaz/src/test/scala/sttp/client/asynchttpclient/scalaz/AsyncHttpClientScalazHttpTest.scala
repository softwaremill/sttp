package sttp.client.asynchttpclient.scalaz

import scalaz.concurrent.Task
import sttp.client.SttpBackend
import sttp.client.impl.scalaz.convertScalazTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientScalazHttpTest extends HttpTest[Task] {

  override val backend: SttpBackend[Task, Any] = AsyncHttpClientScalazBackend().unsafePerformSync
  override implicit val convertToFuture: ConvertToFuture[Task] = convertScalazTaskToFuture

  override def throwsExceptionOnUnsupportedEncoding = false
}
