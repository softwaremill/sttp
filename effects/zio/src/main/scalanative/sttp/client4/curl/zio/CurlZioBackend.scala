package sttp.client4.curl.zio

import _root_.zio._
import sttp.client4.Backend
import sttp.client4.impl.zio.RIOMonadAsyncError

class CurlZioBackend private (verbose: Boolean)
    extends sttp.client4.curl.AbstractSyncCurlBackend[Task](RIOMonadAsyncError[Any], verbose)
    with Backend[Task]

object CurlZioBackend {
  def apply(verbose: Boolean = false): CurlZioBackend = new CurlZioBackend(verbose)

  def layer(verbose: Boolean = false): ZLayer[Any, Throwable, CurlZioBackend] =
    ZLayer.scoped(scoped(verbose))

  def scoped(verbose: Boolean = false): ZIO[Scope, Throwable, CurlZioBackend] =
    ZIO.acquireRelease(ZIO.attempt(apply(verbose)))(_.close().ignore)
}
