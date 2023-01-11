package sttp.client3

import sttp.capabilities.WebSockets

trait WebSocketBackend[F[_]] extends Backend[F] with AbstractBackend[F, WebSockets] {
  def send[T](request: WebSocketRequest[F, T]): F[Response[T]] = internalSend(request)
}
