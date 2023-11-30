package sttp.client4.curl

import sttp.client4._
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.monad.IdMonad
import sttp.monad.TryMonad

import scala.util.Try

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractSyncCurlBackend(IdMonad, verbose) with SyncBackend {}

object CurlBackend {
  def apply(verbose: Boolean = false): SyncBackend = FollowRedirectsBackend(new CurlBackend(verbose))
}

private class CurlTryBackend(verbose: Boolean) extends AbstractSyncCurlBackend(TryMonad, verbose) with Backend[Try] {}

object CurlTryBackend {
  def apply(verbose: Boolean = false): Backend[Try] = FollowRedirectsBackend(new CurlTryBackend(verbose))
}
