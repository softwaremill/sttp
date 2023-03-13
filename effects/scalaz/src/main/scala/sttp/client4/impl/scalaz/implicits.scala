package sttp.client4.impl.scalaz

import scalaz.~>
import sttp.client4.monad.FunctionK
import sttp.client4._
import sttp.client4.wrappers.MappedEffectBackend
import sttp.monad.MonadError

object implicits extends ScalazImplicits

trait ScalazImplicits {
  implicit final def backendToScalazMappable[F[_]](backend: Backend[F]): MappableBackend[F] =
    new MappableBackend(backend)

  implicit final def webSocketBackendToScalazMappable[F[_]](backend: WebSocketBackend[F]): MappableWebSocketBackend[F] =
    new MappableWebSocketBackend(backend)

  implicit final def streambackendToScalazMappable[F[_], S](backend: StreamBackend[F, S]): MappableStreamBackend[F, S] =
    new MappableStreamBackend(backend)

  implicit final def webSocketStreamBackendToScalazMappable[F[_], S](
      backend: WebSocketStreamBackend[F, S]
  ): MappableWebSocketStreamBackend[F, S] =
    new MappableWebSocketStreamBackend(backend)
}

final class MappableBackend[F[_]] private[scalaz] (private val backend: Backend[F]) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): Backend[G] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

final class MappableWebSocketBackend[F[_]] private[scalaz] (private val backend: WebSocketBackend[F]) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): WebSocketBackend[G] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

final class MappableStreamBackend[F[_], S] private[scalaz] (private val backend: StreamBackend[F, S]) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): StreamBackend[G, S] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

final class MappableWebSocketStreamBackend[F[_], S] private[scalaz] (private val backend: WebSocketStreamBackend[F, S])
    extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): WebSocketStreamBackend[G, S] =
    MappedEffectBackend(backend, new AsFunctionK(f), new AsFunctionK(g), implicitly[MonadError[G]])
}

private[scalaz] class AsFunctionK[F[_], G[_]](ab: F ~> G) extends FunctionK[F, G] {
  override def apply[X](x: => F[X]): G[X] = ab(x)
}
