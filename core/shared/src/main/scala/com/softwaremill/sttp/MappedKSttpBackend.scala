package com.softwaremill.sttp
import scala.language.higherKinds

private[sttp] final class MappedKSttpBackend[F[_], -S, G[_]](wrapped: SttpBackend[F, S],
                                                             mapping: FunctionK[F, G],
                                                             val responseMonad: MonadError[G])
    extends SttpBackend[G, S] {
  def send[T](request: Request[T, S]): G[Response[T]] = mapping(wrapped.send(request))

  def close(): Unit = wrapped.close()
}
