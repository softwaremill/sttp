package sttp.client4.curl

import sttp.client4.curl.internal._
import sttp.client4.curl.internal.CurlApi._

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.libc.stdlib._

import java.io.{InputStream, IOException}

/** An InputStream that lazily drives a curl_multi transfer on each read() call.
  *
  * Takes ownership of the multi handle, easy handle, native body buffer, and (optionally) request header slists. All
  * resources are freed on close().
  *
  * The headerLists parameter holds curl_slist pointers for request headers (from CURLOPT_HTTPHEADER). Ownership is
  * transferred here so that the slists outlive the entire transfer, as required by libcurl's contract.
  *
  * IMPORTANT: This InputStream is NOT thread-safe. It must be read from the same thread that created it (which is the
  * normal usage pattern for blocking I/O).
  */
final class CurlMultiInputStream private[curl] (
    multiHandle: CurlMultiHandle,
    easyHandle: CurlHandle,
    bodyResp: Ptr[CurlFetch],
    headersResp: Ptr[CurlFetch],
    httpCode: Ptr[Long],
    headerLists: Seq[CurlList] = Nil
) extends InputStream {

  private var readPos: Int = 0
  private var finished: Boolean = false
  private var closed: Boolean = false
  private var transferError: Option[Int] = None

  private implicit val zone: Zone = Zone.open()
  private val runningHandles: Ptr[CInt] = alloc[CInt](1)
  !runningHandles = 1

  override def read(): Int = {
    ensureOpen()
    val buf = new Array[Byte](1)
    val n = read(buf, 0, 1)
    if (n == -1) -1 else buf(0) & 0xff
  }

  override def read(buf: Array[Byte], off: Int, len: Int): Int = {
    ensureOpen()
    if (len == 0) return 0

    val avail = writePos - readPos
    if (avail > 0) {
      val toCopy = math.min(avail, len)
      copyFromNativeBuffer(buf, off, toCopy)
      readPos += toCopy
      maybeResetBuffer()
      toCopy
    } else if (finished) {
      transferError.foreach { code =>
        throw new IOException(s"curl transfer failed with CURLcode $code")
      }
      -1
    } else {
      resetBuffer()
      pumpUntilDataOrDone()

      val nowAvailable = writePos - readPos
      if (nowAvailable > 0) {
        val toCopy = math.min(nowAvailable, len)
        copyFromNativeBuffer(buf, off, toCopy)
        readPos += toCopy
        maybeResetBuffer()
        toCopy
      } else {
        transferError.foreach { code =>
          throw new IOException(s"curl transfer failed with CURLcode $code")
        }
        -1
      }
    }
  }

  override def available(): Int = {
    if (closed || finished) 0
    else math.max(0, writePos - readPos)
  }

  override def close(): Unit = {
    if (!closed) {
      closed = true
      val _ = multiHandle.removeHandle(easyHandle)
      easyHandle.cleanup()
      multiHandle.cleanup()
      free((!bodyResp)._1)
      free(bodyResp.asInstanceOf[Ptr[Byte]])
      free((!headersResp)._1)
      free(headersResp.asInstanceOf[Ptr[Byte]])
      free(httpCode.asInstanceOf[Ptr[Byte]])
      zone.close()
      // Free request header slists (ownership transferred from Context)
      headerLists.foreach(l => if (l.ptr != null) l.ptr.free())
    }
  }

  // --- Private helpers ---

  private def writePos: Int = {
    val pos = (!bodyResp)._2
    if (pos > Int.MaxValue.toUInt)
      throw new IOException(s"curl write buffer exceeded Int.MaxValue bytes ($pos)")
    pos.toInt
  }

  private def resetBuffer(): Unit = {
    (!bodyResp)._2 = 0.toUInt
    readPos = 0
  }

  private def maybeResetBuffer(): Unit = {
    if (readPos >= writePos) resetBuffer()
  }

  private def copyFromNativeBuffer(buf: Array[Byte], off: Int, len: Int): Unit = {
    val src = (!bodyResp)._1
    var i = 0
    while (i < len) {
      buf(off + i) = !(src + readPos + i)
      i += 1
    }
  }

  private def pumpUntilDataOrDone(): Unit = {
    while (writePos == 0 && !finished) {
      val mc = multiHandle.perform(runningHandles)
      if (mc != CurlMCode.Ok) {
        finished = true
        transferError = Some(-1)
        return
      }

      if (!runningHandles == 0) {
        finished = true
        checkTransferResult()
        return
      }

      if (writePos > 0) return

      val pollResult = multiHandle.poll(1000, null)
      if (pollResult != CurlMCode.Ok) {
        finished = true
        transferError = Some(-1)
        return
      }
    }
  }

  private def checkTransferResult(): Unit = {
    val result = multiHandle.infoReadResult(null)
    if (result > 0) {
      transferError = Some(result)
    }
  }

  private def ensureOpen(): Unit = {
    if (closed) throw new IOException("Stream closed")
  }
}
