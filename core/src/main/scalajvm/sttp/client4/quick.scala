package sttp.client4

object quick extends SttpApi {
  lazy val backend: WebSocketSyncBackend = DefaultSyncBackend()

  implicit class RichRequest[T](val request: Request[T]) {
    def send(): Response[T] = backend.send(request)
  }
}
