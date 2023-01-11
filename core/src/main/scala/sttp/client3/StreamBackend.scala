package sttp.client3

import sttp.capabilities.Effect

trait StreamBackend[F[_], +S] extends Backend[F] with AbstractBackend[F, S] {
  // For some reason StreamRequest is invariant on S
  def send[T, R >: S with Effect[F]](request: StreamRequest[T, R]): F[Response[T]] = internalSend(request)
}
