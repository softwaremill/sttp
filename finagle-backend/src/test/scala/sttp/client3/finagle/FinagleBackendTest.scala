package sttp.client3.finagle

import com.twitter.util.{Return, Throw, Future => TFuture}
import sttp.client3.testing.{ConvertToFuture, HttpTest}

import sttp.client3.{SttpBackend, _}
import scala.concurrent.{Future, Promise}
import sttp.client3.testing.HttpTest.endpoint

class FinagleBackendTest extends HttpTest[TFuture] {

  override val backend: SttpBackend[TFuture, Any] = FinagleBackend()
  override implicit val convertToFuture: ConvertToFuture[TFuture] = new ConvertToFuture[TFuture] {
    override def toFuture[T](value: TFuture[T]): Future[T] = {
      val promise: Promise[T] = Promise()
      value.respond {
        case Return(value)    => promise.success(value)
        case Throw(exception) => promise.failure(exception)
      }
      promise.future
    }
  }
  override def throwsExceptionOnUnsupportedEncoding = false
  override def supportsCustomMultipartContentType = false

  "Host header" - {
    "Should not send the URL's hostname as the host header" in {
      basicRequest.get(uri"$endpoint/echo/headers").header("Host", "test.com").response(asStringAlways).send(backend).toFuture().map { response =>
        response.body should include("Host->test.com")
        response.body should not include "Host->localhost"
      }
    }
  }
}
