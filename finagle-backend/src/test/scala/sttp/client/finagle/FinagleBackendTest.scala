package sttp.client.finagle

import sttp.client.{NothingT, SttpBackend}
import com.twitter.util.{Return, Throw, Future => TFuture}
import sttp.client.testing.{ConvertToFuture, HttpTest}

import scala.concurrent.{Future, Promise}

class FinagleBackendTest extends HttpTest[TFuture] {

  override implicit val backend: SttpBackend[TFuture, Nothing, NothingT] = FinagleBackend()
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
}
