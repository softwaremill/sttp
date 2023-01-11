package sttp.client3

import sttp.capabilities.WebSockets

trait WebSocketStreamBackend[F[_], S]
    extends WebSocketBackend[F]
    with StreamBackend[F, S]
    with AbstractBackend[F, S with WebSockets] {
  def send[T](request: WebSocketStreamRequest[T, S]): F[Response[T]] = internalSend(request)
}
