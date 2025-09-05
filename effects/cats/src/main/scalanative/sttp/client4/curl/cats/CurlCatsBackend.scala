package sttp.client4.curl.cats

import cats.effect.kernel.Sync
import sttp.capabilities.Effect
import sttp.client4.curl.AbstractSyncCurlBackend
import sttp.client4.impl.cats.CatsMonadError
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.Backend

class CurlCatsBackend[F[_]: Sync] private (verbose: Boolean)
    extends AbstractSyncCurlBackend[F](new CatsMonadError[F], verbose)
    with Backend[F] {
  override type R = Effect[F]
}

object CurlCatsBackend {

  /** Create a backend using the cats-effect Sync type class.
    *
    * @param verbose
    *   If true, logs request and response summary to the console.
    * @return
    *   A backend that uses the Sync type class for effects.
    */
  def apply[F[_]: Sync](verbose: Boolean = false): Backend[F] =
    FollowRedirectsBackend(new CurlCatsBackend[F](verbose))
}
