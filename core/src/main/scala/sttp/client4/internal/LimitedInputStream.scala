package sttp.client4.internal

import sttp.capabilities.StreamMaxLengthExceededException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException

private[client4] class FailingLimitedInputStream(in: InputStream, limit: Long) extends LimitedInputStream(in, limit) {
  override def onLimit: Int = {
    throw new StreamMaxLengthExceededException(limit)
  }
}

/** Based on Guava's https://github.com/google/guava/blob/master/guava/src/com/google/common/io/ByteStreams.java */
private[client4] class LimitedInputStream(in: InputStream, limit: Long) extends FilterInputStream(in) {
  protected var left: Long = limit
  private var mark: Long = -1L

  override def available(): Int = Math.min(in.available(), left.toInt)

  override def mark(readLimit: Int): Unit = {
    in.mark(readLimit)
    mark = left
  }

  override def read(): Int = {
    if (left == 0) {
      onLimit
    } else {
      val result = in.read()
      if (result != -1) {
        left -= 1
      }
      result
    }
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    if (left == 0) {
      // Temporarily perform a read to check if more bytes are available
      val checkRead = in.read()
      if (checkRead == -1) {
        -1 // No more bytes available in the stream
      } else {
        onLimit
      }
    } else {
      val adjustedLen = Math.min(len, left.toInt)
      val result = in.read(b, off, adjustedLen)
      if (result != -1) {
        left -= result
      }
      result
    }
  }

  override def reset(): Unit = {
    if (!in.markSupported) {
      throw new IOException("Mark not supported")
    }
    if (mark == -1) {
      throw new IOException("Mark not set")
    }

    in.reset()
    left = mark
  }

  override def skip(n: Long): Long = {
    val toSkip = Math.min(n, left)
    val skipped = in.skip(toSkip)
    left -= skipped
    skipped
  }

  protected def onLimit: Int = -1
}
