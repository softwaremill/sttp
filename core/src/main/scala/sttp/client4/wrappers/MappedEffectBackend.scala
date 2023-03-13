package sttp.client4.wrappers

import sttp.capabilities.Effect
import sttp.client4.monad.{FunctionK, MapEffect}
import sttp.client4.{Backend, GenericBackend, GenericRequest, Response, StreamBackend, WebSocketBackend, WebSocketStreamBackend}
import sttp.monad.MonadError

abstract class MappedEffectBackend[F[_], G[_], P](
    backend: GenericBackend[F, P],
    f: FunctionK[F, G],
    g: FunctionK[G, F],
    m: MonadError[G]
) extends GenericBackend[G, P] {
  override def send[T](request: GenericRequest[T, P with Effect[G]]): G[Response[T]] =
    f(backend.send(MapEffect[G, F, T, P](request, g, f, m, backend.monad)))

  override def close(): G[Unit] = f(backend.close())

  override def monad: MonadError[G] = m
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
