package sttp.client

import sttp.client.monad.{IdMonad, TryMonad}

import scala.util.Try

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractCurlBackend[Identity](IdMonad, verbose) {}

object CurlBackend {
  def apply(verbose: Boolean = false): SttpBackend[Identity, Any, NothingT] =
    new FollowRedirectsBackend[Identity, Any, NothingT](
      new CurlBackend(verbose): SttpBackend[Identity, Any, NothingT]
    )
}

private class CurlTryBackend(verbose: Boolean) extends AbstractCurlBackend[Try](TryMonad, verbose) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false): SttpBackend[Try, Any, NothingT] =
    new FollowRedirectsBackend[Try, Any, NothingT](
      new CurlTryBackend(verbose): SttpBackend[Try, Any, NothingT]
    )
}
