package sttp.client.impl.scalaz

import scalaz.~>
import sttp.client.monad.{FunctionK, MapEffect, MonadError}
import sttp.client.{Effect, Identity, Request, Response, SttpBackend}

object implicits extends ScalazImplicits

trait ScalazImplicits {
  implicit final def sttpBackendToScalazMappableSttpBackend[F[_], P](
      sttpBackend: SttpBackend[F, P]
  ): MappableSttpBackend[F, P] = new MappableSttpBackend(sttpBackend)
}

final class MappableSttpBackend[F[_], P] private[scalaz] (
    private val sttpBackend: SttpBackend[F, P]
) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): SttpBackend[G, P] =
    new MappedKSttpBackend(sttpBackend, f, g, implicitly)
}

private[scalaz] final class MappedKSttpBackend[F[_], +P, WS_HANDLER[_], G[_]](
    wrapped: SttpBackend[F, P],
    f: F ~> G,
    g: G ~> F,
    val responseMonad: MonadError[G]
) extends SttpBackend[G, P] {
  def send[T, R >: P with Effect[G]](request: Request[T, R]): G[Response[T]] =
    f(
      wrapped.send(
        MapEffect[G, F, Identity, T, P](
          request: Request[T, P with Effect[G]],
          asFunctionK(g),
          asFunctionK(f),
          responseMonad,
          wrapped.responseMonad
        )
      )
    )

  def close(): G[Unit] = f(wrapped.close())

  private def asFunctionK[A[_], B[_]](ab: A ~> B) =
    new FunctionK[A, B] {
      override def apply[X](x: A[X]): B[X] = ab(x)
    }
}
