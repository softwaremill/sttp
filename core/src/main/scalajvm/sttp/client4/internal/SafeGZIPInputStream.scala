package sttp.client4.internal

import java.io.{EOFException, FilterInputStream, InputStream}
import java.util.zip.GZIPInputStream

/** Creates GZIPInputStream instances that handle empty streams gracefully, replacing exception-based control flow
  * regarding EOFException with a more principled approach.
  *
  * It addresses a scenario where some HTTP servers may omit the Content-Length header for empty responses with status
  * code 202 (Accepted), preventing issues when content length cannot be determined in advance.
  */
object SafeGZIPInputStream {

  /** Creates a new SafeGZIPInputStream instance.
    *
    * @param in
    *   The input stream to wrap
    * @param bufferSize
    *   The buffer size to use for the GZIP stream (default: 512)
    * @return
    *   A new SafeGZIPInputStream instance
    */
  def apply(in: InputStream, bufferSize: Int = 512): SafeGZIPInputStream = new SafeGZIPInputStream(in, bufferSize)

  /** Used as a fallback when the input stream is empty.
    */
  private lazy val noOpStream: InputStream = new InputStream {
    val endOfStream: Int = -1
    override def read(): Int = endOfStream
    override def read(b: Array[Byte], off: Int, len: Int): Int = endOfStream
  }
}

/** A safe wrapper for GZIPInputStream that handles empty streams gracefully. Prevents EOFException when reading empty
  * GZIP streams by falling back to a no-op stream. All other exceptions are propagated as-is.
  *
  * @param in
  *   The input stream to wrap
  * @param bufferSize
  *   The buffer size to use for the GZIP stream
  */
class SafeGZIPInputStream(in: InputStream, bufferSize: Int) extends FilterInputStream(in) {
  private lazy val stream: InputStream =
    try {
      new GZIPInputStream(in, bufferSize)
    } catch {
      case _: EOFException => SafeGZIPInputStream.noOpStream
    }

  override def read(): Int = stream.read()

  override def read(b: Array[Byte], off: Int, len: Int): Int = stream.read(b, off, len)

  override def close(): Unit = {
    stream.close()
    super.close()
  }
}
