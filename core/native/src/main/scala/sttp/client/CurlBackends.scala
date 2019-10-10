package sttp.client

import sttp.client.monad.{IdMonad, TryMonad}

import scala.util.Try

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractCurlBackend[Identity, Nothing](IdMonad, verbose) {}

object CurlBackend {
  def apply(verbose: Boolean = false): SttpBackend[Identity, Nothing, NothingT] =
    new FollowRedirectsBackend[Identity, Nothing, NothingT](
      new CurlBackend(verbose): SttpBackend[Identity, Nothing, NothingT]
    )
}

private class CurlTryBackend(verbose: Boolean) extends AbstractCurlBackend[Try, Nothing](TryMonad, verbose) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false): SttpBackend[Try, Nothing, NothingT] =
    new FollowRedirectsBackend[Try, Nothing, NothingT](
      new CurlTryBackend(verbose): SttpBackend[Try, Nothing, NothingT]
    )
}
