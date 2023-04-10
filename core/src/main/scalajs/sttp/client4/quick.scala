package sttp.client4

import scala.concurrent.Future

object quick extends SttpApi {
  lazy val backend: Backend[Future] = DefaultFutureBackend()

  implicit class RichRequest[T](val request: Request[T]) {
    def send(): Future[Response[T]] = backend.send(request)
  }
}
