package sttp.client4.curl.cats

import cats.effect.{FileDescriptorPoller, IO, Resource}
import sttp.client4.Backend
import sttp.client4.curl.AbstractCurlBackend
import sttp.client4.curl.internal.CurlApi._
import sttp.client4.curl.internal.{CurlApi, CurlCode, CurlMCode}
import sttp.client4.impl.cats.CatsMonadError
import sttp.client4.wrappers.FollowRedirectsBackend

import java.util.concurrent.atomic.AtomicInteger
import scala.scalanative.libc.stdlib
import scala.scalanative.meta.LinktimeInfo
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

/** A curl backend which never blocks a thread on network I/O.
  *
  * It uses libcurl's multi socket interface (`curl_multi_socket_action`): libcurl reports which sockets it wants to
  * wait on and for how long, and the backend delegates the waiting to the cats-effect runtime's polling system
  * (epoll on Linux, kqueue on macOS), so curl shares the event loop with the rest of the application.
  *
  * libcurl multi handles aren't thread-safe, so each handle is guarded by a fiber-aware mutex (waiting fibers are
  * suspended, no thread is blocked), which is only held for the short, non-blocking libcurl calls. In multi-threaded mode, requests are spread over `parallelism` multi handles to avoid
  * contention; in single-threaded mode, a single handle is used.
  *
  * If the runtime has no [[FileDescriptorPoller]] (e.g. a custom `IORuntime` with the default sleep-based polling
  * system), the backend falls back to driving each transfer from the blocking thread pool.
  */
class CurlCatsAsyncBackend private (drivers: Array[CurlMultiDriver], verbose: Boolean)
    extends AbstractCurlBackend[IO](new CatsMonadError[IO], verbose)
    with Backend[IO] {

  private val next = new AtomicInteger(0)

  override protected def performCurl(c: CurlHandle): IO[CurlCode.CurlCode] =
    FileDescriptorPoller.find.flatMap {
      case Some(poller) =>
        val driver = drivers(Math.floorMod(next.getAndIncrement(), drivers.length))
        driver.perform(poller, c).map(CurlCode(_))
      case None => CurlCatsAsyncBackend.performBlocking(c)
    }

  override def close(): IO[Unit] = drivers.foldLeft(IO.unit)(_ *> _.close)
}

object CurlCatsAsyncBackend {

  /** The default number of multi handles: one per available processor in multi-threaded mode, one otherwise. */
  def defaultParallelism: Int =
    if (LinktimeInfo.isMultithreadingEnabled) Math.max(1, Runtime.getRuntime.availableProcessors()) else 1

  /** Creates a backend, which is closed when the resource is released.
    *
    * @param verbose
    *   If true, logs request and response summary to the console.
    * @param parallelism
    *   The number of libcurl multi handles to spread the requests over.
    */
  def resource(verbose: Boolean = false, parallelism: Int = defaultParallelism): Resource[IO, Backend[IO]] =
    Resource.make(IO(apply(verbose, parallelism)))(_.close())

  /** Creates a backend. It should be closed after use. */
  def apply(verbose: Boolean = false, parallelism: Int = defaultParallelism): Backend[IO] = {
    require(parallelism > 0, "parallelism must be positive")
    FollowRedirectsBackend(new CurlCatsAsyncBackend(Array.fill(parallelism)(CurlMultiDriver()), verbose))
  }

  /** Fallback when there's no poller: a dedicated multi handle per transfer, driven on the blocking pool in short
    * slices, so that the fiber stays cancelable.
    */
  private def performBlocking(c: CurlHandle): IO[CurlCode.CurlCode] =
    IO {
      val multi = CurlApi.multiInit
      val running = stdlib.calloc(1.toUSize, sizeof[CInt]).asInstanceOf[Ptr[CInt]]
      val rc = multi.addHandle(c)
      (multi, running, rc)
    }.bracket { case (multi, running, rc) =>
      def loop: IO[CurlCode.CurlCode] =
        IO.blocking {
          val pc = multi.perform(running)
          if (pc != CurlMCode.Ok) Some(CurlCode.FailedInit)
          else if (!running == 0) Some(CurlCode(Math.max(multi.infoReadResult(null), 0)))
          else {
            val _ = multi.poll(50, null)
            None
          }
        }.flatMap {
          case Some(code) => IO.pure(code)
          case None       => IO.cede *> loop
        }
      if (rc != CurlMCode.Ok) IO.raiseError(new RuntimeException(s"curl_multi_add_handle failed with $rc"))
      else loop
    } { case (multi, running, _) =>
      IO {
        val _ = multi.removeHandle(c)
        multi.cleanup()
        stdlib.free(running.asInstanceOf[Ptr[Byte]])
      }
    }
}
