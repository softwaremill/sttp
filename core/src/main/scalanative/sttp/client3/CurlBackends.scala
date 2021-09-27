package sttp.client3

import sttp.client3.monad.IdMonad
import sttp.monad.TryMonad
import scala.util.Try

import sttp.client3.FollowRedirectsBackend.UriEncoder

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractCurlBackend[Identity](IdMonad, verbose) {}

object CurlBackend {
  def apply(verbose: Boolean = false, uriEncoder: UriEncoder = UriEncoder.DefaultEncoder): SttpBackend[Identity, Any] =
    new FollowRedirectsBackend[Identity, Any](
      new CurlBackend(verbose): SttpBackend[Identity, Any],
      uriEncoder = uriEncoder
    )
}

private class CurlTryBackend(verbose: Boolean) extends AbstractCurlBackend[Try](TryMonad, verbose) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false, uriEncoder: UriEncoder = UriEncoder.DefaultEncoder): SttpBackend[Try, Any] =
    new FollowRedirectsBackend[Try, Any](
      new CurlTryBackend(verbose): SttpBackend[Try, Any],
      uriEncoder = uriEncoder
    )
}
