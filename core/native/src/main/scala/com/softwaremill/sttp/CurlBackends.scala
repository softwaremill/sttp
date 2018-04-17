package com.softwaremill.sttp

import scala.concurrent.{ExecutionContext, Future}
import scala.scalanative.native.Zone
import scala.util.Try

class CurlBackend(verbose: Boolean = false)(implicit z: Zone)
    extends AbstractCurlBackend[Id, Nothing](IdMonad, verbose)(z) {}

object CurlBackend {
  def apply(verbose: Boolean = false)(implicit z: Zone) = new CurlBackend(verbose)(z)
}

class CurlTryBackend(verbose: Boolean = false)(implicit z: Zone)
    extends AbstractCurlBackend[Try, Nothing](TryMonad, verbose)(z) {}

object CurlTryBackend {
  def apply(verbose: Boolean = false)(implicit z: Zone) = new CurlTryBackend(verbose)(z)
}

class CurlFutureBackend(verbose: Boolean = false)(implicit z: Zone, ec: ExecutionContext)
    extends AbstractCurlBackend[Future, Nothing](new FutureMonad()(ec), verbose)(z) {}

object CurlFutureBackend {
  def apply(verbose: Boolean = false)(implicit z: Zone, ec: ExecutionContext) = new CurlFutureBackend(verbose)(z, ec)
}
