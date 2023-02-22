package sttp.client3

import sttp.monad.MonadError
import sttp.client3.monad.IdMonad

trait SyncBackend extends Backend[Identity] {
  override def responseMonad: MonadError[Identity] = IdMonad
}
