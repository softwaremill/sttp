package sttp.client4.curl.cats

import cats.effect.{FiberIO, IO, SyncIO}
import cats.effect.std.Mutex
import cats.effect.{FileDescriptorPoller, FileDescriptorPollHandle}
import sttp.client4.curl.internal.CurlApi._
import sttp.client4.curl.internal._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.concurrent.duration._
import scala.scalanative.libc.stdlib
import scala.scalanative.runtime.{fromRawPtr, toRawPtr, Intrinsics}
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

/** Drives a single libcurl multi handle using the `curl_multi_socket_action` interface, integrated with cats-effect's
  * [[FileDescriptorPoller]] (epoll/kqueue). No thread is ever blocked waiting for network I/O: curl tells us (through
  * the socket callback) which sockets it is interested in, we register them with the runtime's poller, and call
  * `curl_multi_socket_action` when they become ready. Timeouts requested by curl (timer callback) are scheduled with
  * `IO.sleep`.
  *
  * Thread-safety: libcurl multi handles can be used from different threads, but never concurrently. Every call into the
  * multi handle is made while holding this driver's monitor (the callbacks are invoked synchronously from within those
  * calls, so they run under the monitor as well, and only record state). All the effectful work (starting watchers,
  * completing requests) happens outside of the monitor, so the lock is only held for the short, non-blocking libcurl
  * calls. Fibers can freely migrate between the worker threads.
  *
  * To scale across worker threads, the backend uses several drivers.
  */
private[cats] final class CurlMultiDriver private () {
  import CurlMultiDriver._

  private val id: Long = nextId.incrementAndGet()
  private val multi: CurlMultiHandle = CurlApi.multiInit
  private val runningPtr: Ptr[CInt] = stdlib.calloc(1.toUSize, sizeof[CInt]).asInstanceOf[Ptr[CInt]]
  private val easyOut: Ptr[Ptr[Curl]] = stdlib.calloc(1.toUSize, sizeof[Ptr[Curl]]).asInstanceOf[Ptr[Ptr[Curl]]]
  @volatile private var poller: FileDescriptorPoller = _
  private var closed = false

  // --- state guarded by `this` (the monitor) ---
  private final class Sock(var what: Int, val gen: Long) { var gained: Int = 0 }
  private val sockets = mutable.HashMap.empty[Int, Sock]
  private var socketGen = 0L
  private var timeoutMs: Long = -1L
  private var timerGen = 0L
  private val completions = mutable.HashMap.empty[Long, Either[Throwable, Int] => Unit]

  // --- state guarded by `reconcileMutex` ---
  private val reconcileMutex: Mutex[IO] = Mutex.in[SyncIO, IO].unsafeRunSync()
  private final class Watcher(val gen: Long, val fiber: FiberIO[Unit])
  private val watchers = mutable.HashMap.empty[Int, Watcher]
  // fibers of removed watchers; a new watcher on the same fd number waits for the old one to deregister first
  private val retired = mutable.HashMap.empty[Int, FiberIO[Unit]]
  private var timer: Option[(Long, FiberIO[Unit])] = None

  {
    registry.put(id, this)
    val userp = idToPtr(id)
    CCurl.multiSetoptPtr(multi, MultiSocketFunction, CFuncPtr.toPtr(socketCallback))
    CCurl.multiSetoptPtr(multi, MultiSocketData, userp)
    CCurl.multiSetoptPtr(multi, MultiTimerFunction, CFuncPtr.toPtr(timerCallback))
    CCurl.multiSetoptPtr(multi, MultiTimerData, userp)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // callbacks (invoked by libcurl, under the monitor)

  private def onSocket(fd: Int, what: Int): Unit =
    if (what == PollRemove) { val _ = sockets.remove(fd) }
    else
      sockets.get(fd) match {
        case Some(s) =>
          s.gained |= (what & ~s.what)
          s.what = what
        case None =>
          socketGen += 1
          sockets.put(fd, new Sock(what, socketGen))
      }

  private def onTimer(ms: Long): Unit = {
    timeoutMs = ms
    timerGen += 1
  }

  // ---------------------------------------------------------------------------------------------------------------
  // public API

  /** Performs the transfer of `easy`, which must be fully configured. The handle is not cleaned up. */
  def perform(fdPoller: FileDescriptorPoller, easy: CurlHandle): IO[Int] = IO.async[Int] { cb =>
    poller = fdPoller
    val key = easy.toLong
    IO {
      synchronized {
        if (closed) { cb(Left(new IllegalStateException("The curl backend is closed"))); false }
        else {
          completions.put(key, cb)
          val rc = multi.addHandle(easy)
          if (rc != CurlMCode.Ok) {
            completions.remove(key)
            cb(Left(new RuntimeException(s"curl_multi_add_handle failed with $rc")))
            false
          } else true
        }
      }
    }.flatMap { added =>
      if (added) reconcile.as(Some(cancel(easy, key)))
      else IO.pure(None)
    }
  }

  def close: IO[Unit] =
    reconcileMutex.lock.surround {
      val cancelAll = watchers.values.map(_.fiber).toList ++ timer.map(_._2).toList
      IO { watchers.clear(); timer = None } *> cancelAll.foldLeft(IO.unit)(_ *> cancelAsync(_))
    } *> IO {
      synchronized {
        if (!closed) {
          closed = true
          multi.cleanup()
          registry.remove(id)
          stdlib.free(runningPtr.asInstanceOf[Ptr[Byte]])
          stdlib.free(easyOut.asInstanceOf[Ptr[Byte]])
        }
      }
    }

  // ---------------------------------------------------------------------------------------------------------------
  // internals

  private def cancel(easy: CurlHandle, key: Long): IO[Unit] =
    IO {
      synchronized {
        if (!closed && completions.remove(key).isDefined) {
          val _ = multi.removeHandle(easy)
          easy.cleanup()
        }
      }
    } *> reconcile

  /** Runs a libcurl multi call under the monitor, then completes finished transfers and brings the watchers/timer in
    * line with what curl asked for.
    */
  private def pump(call: => Unit): IO[Unit] =
    IO {
      synchronized {
        if (closed) Nil
        else {
          call
          collectDone()
        }
      }
    }.flatMap { done =>
      IO {
        done.foreach { case (cb, code) =>
          cb(Right(code))
        }
      } *> reconcile
    }

  /** Must be called under the monitor. */
  private def collectDone(): List[(Either[Throwable, Int] => Unit, Int)] = {
    var res = List.empty[(Either[Throwable, Int] => Unit, Int)]
    var code = multi.infoReadResult(easyOut)
    while (code != -1) {
      val easy = !easyOut
      val _ = multi.removeHandle(easy)
      completions.remove(easy.toLong).foreach(cb => res = (cb, code) :: res)
      code = multi.infoReadResult(easyOut)
    }
    res
  }

  private def reconcile: IO[Unit] =
    reconcileMutex.lock.surround {
      IO {
        synchronized {
          val desired = sockets.map { case (fd, s) => (fd, s.gen) }.toMap
          val kicks = sockets.collect { case (fd, s) if s.gained != 0 => (fd, s.gained) }.toList
          sockets.valuesIterator.foreach(_.gained = 0)
          (desired, kicks, timerGen, timeoutMs)
        }
      }.flatMap { case (desired, kicks, tGen, tMs) =>
        val stale = watchers.collect { case (fd, w) if !desired.get(fd).contains(w.gen) => fd }.toList
        val stop = stale.foldLeft(IO.unit) { (acc, fd) =>
          val w = watchers.remove(fd).get
          retired.put(fd, w.fiber)
          acc *> cancelAsync(w.fiber)
        }
        val start = desired.toList.foldLeft(IO.unit) { case (acc, (fd, gen)) =>
          if (watchers.contains(fd)) acc
          else acc *> startWatcher(fd, retired.remove(fd)).map(f => watchers.put(fd, new Watcher(gen, f))).void
        }
        val timerStep: IO[Boolean] =
          if (timer.exists(_._1 == tGen)) IO.pure(false)
          else {
            val old = timer
            timer = None
            val cancelOld = old.fold(IO.unit)(t => cancelAsync(t._2))
            if (tMs < 0) cancelOld.as(false)
            else if (tMs == 0) cancelOld.as(true)
            else
              cancelOld *> (IO.sleep(tMs.millis) *> IO.uncancelable(_ => pump(timeoutAction()))).start
                .map { f => timer = Some((tGen, f)); false }
          }
        (stop *> start *> timerStep).map(fireNow => (kicks, fireNow))
      }
    }.flatMap { case (kicks, fireNow) =>
      val fire = if (fireNow) IO.uncancelable(_ => pump(timeoutAction())) else IO.unit
      // the interest of a socket was extended: its readiness edge might have been consumed already
      val kick = kicks.foldLeft(IO.unit) { case (acc, (fd, gained)) =>
        acc *> (if ((gained & PollIn) != 0) serve(fd, PollIn) else IO.unit) *>
          (if ((gained & PollOut) != 0) serve(fd, PollOut) else IO.unit)
      }
      fire *> kick
    }

  /** Cancels without waiting: the fiber being cancelled might be the one running this code. */
  private def cancelAsync(f: FiberIO[Unit]): IO[Unit] = f.cancel.start.void

  private def timeoutAction(): Unit = { val _ = CCurl.multiSocketAction(multi, SocketTimeout, 0, runningPtr) }

  private def startWatcher(fd: Int, previous: Option[FiberIO[Unit]]): IO[FiberIO[Unit]] = {
    val blocked: IO[Either[Unit, Nothing]] = IO.pure(Left(()))
    def loop(h: FileDescriptorPollHandle): IO[Unit] =
      IO.race(
        h.pollReadRec[Unit, Nothing](())(_ => serve(fd, PollIn) *> blocked),
        h.pollWriteRec[Unit, Nothing](())(_ => serve(fd, PollOut) *> blocked)
      ).void
    (previous.fold(IO.unit)(_.join.void) *>
      poller.registerFileDescriptor(fd, monitorReadReady = true, monitorWriteReady = true).use(loop))
      // deregistering fails if curl already closed the socket; there is nothing to do about it then
      .handleError(_ => ())
      .start
  }

  /** The readiness notifications are edge-triggered (epoll), so we have to keep driving curl as long as the socket
    * stays ready and curl is interested in it - curl might not drain it in one go.
    */
  private def serve(fd: Int, flag: Int): IO[Unit] = {
    def interested: Boolean = synchronized(sockets.get(fd).exists(s => (s.what & flag) != 0))
    def go(n: Int): IO[Unit] =
      IO(interested && CCurl.fdReady(fd, flag) != 0).flatMap {
        case false => IO.unit
        case true =>
          // curl must not be interrupted half-way, as e.g. a completion has to be delivered
          IO.uncancelable(_ => pump { val _ = CCurl.multiSocketAction(multi, fd, flag, runningPtr) }) *>
            (if (n >= 31) IO.cede *> go(0) else go(n + 1))
      }
    go(0)
  }
}

private[cats] object CurlMultiDriver {
  // CURL_POLL_* (also the same values as CURL_CSELECT_IN/OUT)
  private val PollIn = 1
  private val PollOut = 2
  private val PollRemove = 4
  private val SocketTimeout = -1

  // CURLMOPT_*
  private val MultiSocketFunction = 20001
  private val MultiSocketData = 10002
  private val MultiTimerFunction = 20004
  private val MultiTimerData = 10005

  private val nextId = new AtomicLong(0)
  private val registry = new ConcurrentHashMap[Long, CurlMultiDriver]()

  private def idToPtr(id: Long): Ptr[Byte] = fromRawPtr[Byte](Intrinsics.castLongToRawPtr(id))
  private def ptrToId(p: Ptr[Byte]): Long = Intrinsics.castRawPtrToLong(toRawPtr(p))

  private val socketCallback: CFuncPtr5[Ptr[Curl], CInt, CInt, Ptr[Byte], Ptr[Byte], CInt] =
    (_: Ptr[Curl], fd: CInt, what: CInt, userp: Ptr[Byte], _: Ptr[Byte]) => {
      val d = registry.get(ptrToId(userp))
      if (d != null) d.onSocket(fd, what)
      0
    }

  private val timerCallback: CFuncPtr3[Ptr[CurlM], CLong, Ptr[Byte], CInt] =
    (_: Ptr[CurlM], ms: CLong, userp: Ptr[Byte]) => {
      val d = registry.get(ptrToId(userp))
      if (d != null) d.onTimer(ms.toLong)
      0
    }

  def apply(): CurlMultiDriver = new CurlMultiDriver()
}
