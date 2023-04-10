package sttp.client4.finagle

import com.twitter.util.{Future => TFuture, Return, Throw}
import sttp.client4.testing.{ConvertToFuture, HttpTest}

import sttp.client4.Backend
import scala.concurrent.{Future, Promise}

class FinagleBackendTest extends HttpTest[TFuture] {

  override val backend: Backend[TFuture] = FinagleBackend()
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
  override def supportsAutoDecompressionDisabling = false

  override def supportsCancellation: Boolean = false
  override def timeoutToNone[T](t: TFuture[T], timeoutMillis: Int): TFuture[Option[T]] = t.map(Some(_))
}
