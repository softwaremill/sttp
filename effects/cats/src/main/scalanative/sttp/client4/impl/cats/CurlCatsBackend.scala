package sttp.client4.impl.cats

import cats.effect.kernel.Sync
import sttp.client4._
import sttp.client4.curl.internal.CurlApi._
import sttp.client4.curl.AbstractCurlBackend
import sttp.client4.curl.internal.CurlCode.CurlCode
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.testing.BackendStub

class CurlCatsBackend[F[_]: Sync] private (
    verbose: Boolean
) extends AbstractCurlBackend[F](new CatsMonadError, verbose)
    with Backend[F] {

  override def performCurl(c: CurlHandle): F[CurlCode] = Sync[F].blocking(c.perform)

}

object CurlCatsBackend {
  def apply[F[_]: Sync](verbose: Boolean = false): Backend[F] =
    FollowRedirectsBackend(new CurlCatsBackend[F](verbose))

  def stub[F[_]: Sync]: BackendStub[F] = BackendStub(new CatsMonadError)

}
