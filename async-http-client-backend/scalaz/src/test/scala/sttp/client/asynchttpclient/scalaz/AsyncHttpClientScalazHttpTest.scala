package sttp.client.asynchttpclient.scalaz

import scalaz.concurrent.Task
import sttp.client.{NothingT, SttpBackend}
import sttp.client.impl.scalaz.convertScalazTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}

class AsyncHttpClientScalazHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Any, NothingT] = AsyncHttpClientScalazBackend().unsafePerformSync
  override implicit val convertToFuture: ConvertToFuture[Task] = convertScalazTaskToFuture

  override def throwsExceptionOnUnsupportedEncoding = false
}
