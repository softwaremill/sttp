package com.softwaremill.sttp

import scala.util.Try

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractCurlBackend[Id, Nothing](IdMonad, verbose) {}

object CurlBackend {
  def apply(verbose: Boolean = false): SttpBackend[Id, Nothing] =
    new FollowRedirectsBackend[Id, Nothing](
      new CurlBackend(verbose): SttpBackend[Id, Nothing]
    )
}

private class CurlTryBackend(verbose: Boolean) extends AbstractCurlBackend[Try, Nothing](TryMonad, verbose) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false): SttpBackend[Try, Nothing] =
    new FollowRedirectsBackend[Try, Nothing](
      new CurlTryBackend(verbose): SttpBackend[Try, Nothing]
    )
}
