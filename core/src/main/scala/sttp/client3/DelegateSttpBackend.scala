package sttp.client3

import sttp.monad.MonadError

/** A base class for delegate backends, which includes delegating implementations for `close` and `responseMonad`,
  * so that only `send` needs to be defined.
  */
abstract class DelegateSttpBackend[F[_], +P](delegate: SttpBackend[F, P]) extends SttpBackend[F, P] {
  override def close(): F[Unit] = delegate.close()
  override implicit def responseMonad: MonadError[F] = delegate.responseMonad
}
