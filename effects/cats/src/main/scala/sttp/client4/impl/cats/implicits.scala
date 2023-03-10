package sttp.client4.impl.cats

import cats.effect.kernel.{Sync, Async}
import cats.~>
import sttp.client4.monad.FunctionK
import sttp.client4._
import sttp.monad.{MonadAsyncError, MonadError}

object implicits extends CatsImplicits

trait CatsImplicits extends LowerLevelCatsImplicits {
  implicit final def backendToCatsMappable[F[_]](backend: Backend[F]): MappableBackend[F] =
    new MappableBackend(backend)

  implicit final def webSocketBackendToCatsMappable[F[_]](backend: WebSocketBackend[F]): MappableWebSocketBackend[F] =
    new MappableWebSocketBackend(backend)

  implicit final def streamBackendToCatsMappable[F[_], S](backend: StreamBackend[F, S]): MappableStreamBackend[F, S] =
    new MappableStreamBackend(backend)

  implicit final def webSocketStreamBackendToCatsMappable[F[_], S](
      backend: WebSocketStreamBackend[F, S]
  ): MappableWebSocketStreamBackend[F, S] = new MappableWebSocketStreamBackend(backend)

  implicit final def asyncMonadError[F[_]: Async]: MonadAsyncError[F] = new CatsMonadAsyncError[F]
}

trait LowerLevelCatsImplicits {
  implicit final def monadError[F[_]: Sync]: MonadError[F] = new CatsMonadError[F]
}

final class MappableBackend[F[_]] private[cats] (private[cats] val backend: Backend[F]) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): Backend[G] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

final class MappableWebSocketBackend[F[_]] private[cats] (private[cats] val backend: WebSocketBackend[F])
    extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): WebSocketBackend[G] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

final class MappableStreamBackend[F[_], S] private[cats] (private[cats] val backend: StreamBackend[F, S])
    extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): StreamBackend[G, S] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

final class MappableWebSocketStreamBackend[F[_], S] private[cats] (
    private[cats] val backend: WebSocketStreamBackend[F, S]
) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): WebSocketStreamBackend[G, S] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

private[cats] class AsFunctionK[F[_], G[_]](ab: F ~> G) extends FunctionK[F, G] {
  override def apply[X](x: F[X]): G[X] = ab(x)
}
