package com.softwaremill.sttp

import scala.util.Try

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractCurlBackend[Identity, Nothing](IdMonad, verbose) {}

object CurlBackend {
  def apply(verbose: Boolean = false): SttpBackend[Identity, Nothing] =
    new FollowRedirectsBackend[Identity, Nothing](
      new CurlBackend(verbose): SttpBackend[Identity, Nothing]
    )
}

private class CurlTryBackend(verbose: Boolean) extends AbstractCurlBackend[Try, Nothing](TryMonad, verbose) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false): SttpBackend[Try, Nothing] =
    new FollowRedirectsBackend[Try, Nothing](
      new CurlTryBackend(verbose): SttpBackend[Try, Nothing]
    )
}
