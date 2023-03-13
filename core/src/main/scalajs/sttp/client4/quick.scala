package sttp.client4

import sttp.client4.fetch.FetchBackend

import scala.concurrent.Future

object quick extends SttpApi {
  lazy val backend: Backend[Future] = FetchBackend()

  implicit class RichRequest[T](val request: Request[T]) {
    def send(): Future[Response[T]] = backend.send(request)
  }
}
