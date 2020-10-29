package sttp.client3.impl.cats

import cats.effect.Async
import cats.~>
import sttp.capabilities.Effect
import sttp.client3.monad.{FunctionK, MapEffect}
import sttp.client3.{Identity, Request, Response, SttpBackend}
import sttp.monad.{MonadAsyncError, MonadError}

object implicits extends CatsImplicits

trait CatsImplicits {
  implicit final def sttpBackendToCatsMappableSttpBackend[R[_], P](
      sttpBackend: SttpBackend[R, P]
  ): MappableSttpBackend[R, P] = new MappableSttpBackend(sttpBackend)

  implicit final def asyncMonadError[F[_]: Async]: MonadAsyncError[F] = new CatsMonadAsyncError[F]
}

final class MappableSttpBackend[F[_], P] private[cats] (
    private val sttpBackend: SttpBackend[F, P]
) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G, g: G ~> F): SttpBackend[G, P] =
    new MappedKSttpBackend(sttpBackend, f, g, implicitly)
}

private[cats] final class MappedKSttpBackend[F[_], +P, G[_]](
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
