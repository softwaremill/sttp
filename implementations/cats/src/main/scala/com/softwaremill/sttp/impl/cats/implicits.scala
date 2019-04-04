package com.softwaremill.sttp.impl.cats

import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}
import cats.~>

import scala.language.higherKinds

object implicits extends CatsImplicits

trait CatsImplicits {
  implicit def sttpBackendToCatsMappableSttpBackend[R[_], S](
      sttpBackend: SttpBackend[R, S]
  ): MappableSttpBackend[R, S] = new MappableSttpBackend(sttpBackend)
}

class MappableSttpBackend[R[_], S] private[cats] (val sttpBackend: SttpBackend[R, S]) extends AnyVal {
  def mapK[G[_]: MonadError](f: R ~> G): SttpBackend[G, S] =
    new MappedKSttpBackend(sttpBackend, f, implicitly)
}

private[cats] final class MappedKSttpBackend[F[_], -S, G[_]](
    wrapped: SttpBackend[F, S],
    mapping: F ~> G,
    val responseMonad: MonadError[G]
) extends SttpBackend[G, S] {
  def send[T](request: Request[T, S]): G[Response[T]] = mapping(wrapped.send(request))

  def close(): Unit = wrapped.close()
}
