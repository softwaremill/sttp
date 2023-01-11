package sttp.client3

import sttp.client3.monad.MapEffect
import sttp.capabilities.Effect
import sttp.monad.MonadError
import sttp.client3.monad.FunctionK

abstract class MappedEffectBackend[F[_], G[_], P](
    backend: AbstractBackend[F, P],
    f: FunctionK[F, G],
    g: FunctionK[G, F],
    m: MonadError[G]
) extends AbstractBackend[G, P] {
  override def internalSend[T](request: AbstractRequest[T, P with Effect[G]]): G[Response[T]] =
    f(backend.internalSend(MapEffect[G, F, T, P](request, g, f, m, backend.responseMonad)))

  override def close(): G[Unit] = f(backend.close())

  override def responseMonad: MonadError[G] = m
}

object MappedEffectBackend {
  def apply[F[_], G[_]](backend: Backend[F], f: FunctionK[F, G], g: FunctionK[G, F], m: MonadError[G]): Backend[G] =
    new MappedEffectBackend(backend, f, g, m) with Backend[G] {}

  def apply[F[_], G[_]](
      backend: WebSocketBackend[F],
      f: FunctionK[F, G],
      g: FunctionK[G, F],
      m: MonadError[G]
  ): WebSocketBackend[G] =
    new MappedEffectBackend(backend, f, g, m) with WebSocketBackend[G] {}

  def apply[F[_], G[_], S](
      backend: StreamBackend[F, S],
      f: FunctionK[F, G],
      g: FunctionK[G, F],
      m: MonadError[G]
  ): StreamBackend[G, S] =
    new MappedEffectBackend(backend, f, g, m) with StreamBackend[G, S] {}

  def apply[F[_], G[_], S](
      backend: WebSocketStreamBackend[F, S],
      f: FunctionK[F, G],
      g: FunctionK[G, F],
      m: MonadError[G]
  ): WebSocketStreamBackend[G, S] =
    new MappedEffectBackend(backend, f, g, m) with WebSocketStreamBackend[G, S] {}
}
