package sttp.client3

trait Backend[F[_]] extends AbstractBackend[F, Any] {
  def send[T](request: Request[T]): F[Response[T]] = internalSend(request)
}
