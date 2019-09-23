package sttp.client.impl.scalaz

import scalaz.~>
import sttp.client.monad.MonadError
import sttp.client.{Request, Response, SttpBackend}

import scala.language.higherKinds

object implicits extends ScalazImplicits

trait ScalazImplicits {
  implicit def sttpBackendToScalazMappableSttpBackend[R[_], S](
      sttpBackend: SttpBackend[R, S]
  ): MappableSttpBackend[R, S] = new MappableSttpBackend(sttpBackend)
}

class MappableSttpBackend[R[_], S] private[scalaz] (val sttpBackend: SttpBackend[R, S]) extends AnyVal {
  def mapK[G[_]: MonadError](f: R ~> G): SttpBackend[G, S] =
    new MappedKSttpBackend(sttpBackend, f, implicitly)
}

private[scalaz] final class MappedKSttpBackend[F[_], -S, G[_]](
    wrapped: SttpBackend[F, S],
    mapping: F ~> G,
    val responseMonad: MonadError[G]
) extends SttpBackend[G, S] {
  def send[T](request: Request[T, S]): G[Response[T]] = mapping(wrapped.send(request))

  def close(): G[Unit] = mapping(wrapped.close())
}
