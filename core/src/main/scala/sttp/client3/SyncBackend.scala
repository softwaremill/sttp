package sttp.client3

import sttp.monad.MonadError
import sttp.client3.monad.IdMonad

trait SyncBackend extends Backend[Identity] {
  override def send[T](request: Request[T]): Response[T] = internalSend(request)

  override def responseMonad: MonadError[Identity] = IdMonad
}
