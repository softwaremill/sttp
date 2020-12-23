package sttp.client3

import sttp.capabilities.Effect
import sttp.client3.monad.{FunctionK, MapEffect}
import sttp.monad.{MonadError, TryMonad}

import scala.util.Try

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Try`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam P TODO
  */
class TryBackend[P](delegate: SttpBackend[Identity, P]) extends SttpBackend[Try, P] {
  override def send[T, R >: P with Effect[Try]](request: Request[T, R]): Try[Response[T]] =
    Try(
      delegate.send(
        MapEffect[Try, Identity, T, P](
          request: Request[T, P with Effect[Try]],
          tryToId,
          idToTry,
          responseMonad,
          delegate.responseMonad
        )
      )
    )

  override def close(): Try[Unit] = Try(delegate.close())

  override def responseMonad: MonadError[Try] = TryMonad

  private val tryToId: FunctionK[Try, Identity] =
    new FunctionK[Try, Identity] {
      override def apply[A](fa: Try[A]): Identity[A] = fa.get
    }

  private val idToTry: FunctionK[Identity, Try] =
    new FunctionK[Identity, Try] {
      override def apply[A](fa: Identity[A]): Try[A] = Try(fa)
    }
}
