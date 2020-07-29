package sttp.client.impl.scalaz

import scalaz.~>
import sttp.client.monad.{FunctionK, MonadError}
import sttp.client.{Effect, Request, Response, SttpBackend}

object implicits extends ScalazImplicits

trait ScalazImplicits {
  implicit final def sttpBackendToScalazMappableSttpBackend[F[_], P, WS_HANDLER[_]](
      sttpBackend: SttpBackend[F, P, WS_HANDLER]
  ): MappableSttpBackend[F, P, WS_HANDLER] = new MappableSttpBackend(sttpBackend)
}

final class MappableSttpBackend[F[_], P, WS_HANDLER[_]] private[scalaz] (
    private val sttpBackend: SttpBackend[F, P, WS_HANDLER]
) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): SttpBackend[G, P, WS_HANDLER] =
    new MappedKSttpBackend(sttpBackend, f, g, implicitly)
}

private[scalaz] final class MappedKSttpBackend[F[_], +P, WS_HANDLER[_], G[_]](
    wrapped: SttpBackend[F, P, WS_HANDLER],
    f: F ~> G,
    g: G ~> F,
    val responseMonad: MonadError[G]
) extends SttpBackend[G, P, WS_HANDLER] {
  def send[T, R >: P with Effect[G]](request: Request[T, R]): G[Response[T]] =
    f(wrapped.send((request: Request[T, P with Effect[G]]).mapEffect[G, F, P](gAsFunctionK)))

  override def openWebsocket[T, WS_RESULT, R >: P with Effect[G]](
      request: Request[T, R],
      handler: WS_HANDLER[WS_RESULT]
  ): G[WebSocketResponse[WS_RESULT]] =
    f(wrapped.openWebsocket((request: Request[T, P with Effect[G]]).mapEffect[G, F, P](gAsFunctionK), handler))

  def close(): G[Unit] = f(wrapped.close())

  private def gAsFunctionK =
    new FunctionK[G, F] {
      override def apply[A](fa: G[A]): F[A] = g(fa)
    }
}
