package sttp.client.httpclient.monix

import monix.eval.Task
import sttp.client.impl.monix.convertMonixTaskToFuture
import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client._
import monix.execution.Scheduler.Implicits.global
import sttp.client.testing.HttpTest.endpoint
import sttp.model.Header

class HttpClientMonixHttpTest extends HttpTest[Task] {

  override implicit val backend: SttpBackend[Task, Nothing, NothingT] = HttpClientMonixBackend().runSyncUnsafe()
  override implicit val convertToFuture: ConvertToFuture[Task] = convertMonixTaskToFuture

  "should not duplicate headers when reusing call definition" in {
    val call = basicRequest
      .get(uri"$endpoint/echo/headers")
      .header("foo", "bar")
      .send()
    call
      .flatMap(_ => call)
      .toFuture()
      .map { response =>
        response.headers.filter(_.name == "foo") shouldBe List(Header("foo", "bar"))
      }
  }
}
