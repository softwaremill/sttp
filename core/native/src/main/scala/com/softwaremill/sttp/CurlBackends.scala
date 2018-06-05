package com.softwaremill.sttp

import scala.concurrent.{ExecutionContext, Future}
import scala.scalanative.native.Zone
import scala.util.Try

class CurlBackend(verbose: Boolean = false)(implicit z: Zone)
    extends AbstractCurlBackend[Id, Nothing](IdMonad, verbose)(z) {}

object CurlBackend {
  def apply(verbose: Boolean = false)(implicit z: Zone): SttpBackend[Id, Nothing] =
    new FollowRedirectsBackend[Id, Nothing](
      new CurlBackend(verbose)(z): SttpBackend[Id, Nothing]
    )
}

class CurlTryBackend(verbose: Boolean = false)(implicit z: Zone)
    extends AbstractCurlBackend[Try, Nothing](TryMonad, verbose)(z) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false)(implicit z: Zone): SttpBackend[Try, Nothing] =
    new FollowRedirectsBackend[Try, Nothing](
      new CurlTryBackend(verbose)(z): SttpBackend[Try, Nothing]
    )
}

class CurlFutureBackend(verbose: Boolean = false)(implicit z: Zone, ec: ExecutionContext)
    extends AbstractCurlBackend[Future, Nothing](new FutureMonad()(ec), verbose)(z) {}

object CurlFutureBackend {
  def apply(verbose: Boolean = false)(implicit z: Zone, ec: ExecutionContext): SttpBackend[Future, Nothing] =
    new FollowRedirectsBackend[Future, Nothing](
      new CurlFutureBackend(verbose)(z, ec): SttpBackend[Future, Nothing]
    )
}
