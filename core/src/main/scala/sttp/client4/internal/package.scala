package sttp.client4

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer
import scala.annotation.{implicitNotFound, tailrec}
import scala.collection.immutable.Queue

package object internal {
  private[client4] def contentTypeWithCharset(ct: String, charset: String): String =
    s"$ct; charset=$charset"

  private[client4] def charsetFromContentType(ct: String): Option[String] =
    ct.split(";").map(_.trim.toLowerCase).collectFirst {
      case s if s.startsWith("charset=") && s.substring(8).trim != "" => s.substring(8).trim
    }

  private[client4] def transfer(is: InputStream, os: OutputStream): Unit = {
    var read = 0
    val buf = new Array[Byte](IOBufferSize)

    @tailrec
    def transfer(): Unit = {
      read = is.read(buf, 0, buf.length)
      if (read != -1) {
        os.write(buf, 0, read)
        transfer()
      }
    }

    transfer()
  }

  private[client4] def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    transfer(is, os)
    os.toByteArray
  }

  private[client4] def emptyInputStream(): InputStream = new ByteArrayInputStream(Array[Byte]())

  private[client4] def enqueueBytes(
      queue: Queue[Array[Byte]],
      bytes: ByteBuffer
  ): Queue[Array[Byte]] = queue.enqueue(bytes.array())

  private[client4] def concatBytes(queue: Queue[Array[Byte]]): Array[Byte] = {
    val size = queue.map(_.length).sum
    val bytes = ByteBuffer.allocate(size)
    queue.foreach(bytes.put)
    bytes.array()
  }

  /** Removes quotes surrounding the charset.
    */
  private[client4] def sanitizeCharset(charset: String): String = {
    val c2 = charset.trim()
    val c3 = if (c2.startsWith("\"")) c2.substring(1) else c2
    if (c3.endsWith("\"")) c3.substring(0, c3.length - 1) else c3
  }

  @implicitNotFound(
    "This is a partial request, the method & url are not specified. Use " +
      ".get(...), .post(...) etc. to obtain a non-partial request."
  )
  private[client4] type IsIdInRequest[U[_]] = U[Unit] =:= Identity[Unit]

  private[client4] val Utf8 = "utf-8"
  private[client4] val Iso88591 = "iso-8859-1"
  private[client4] val CrLf = "\r\n"

  private[client4] def throwNestedMultipartNotAllowed =
    throw new IllegalArgumentException("Nested multipart bodies are not allowed")

  private[client4] type ReplayableBody = Option[Either[Array[Byte], SttpFile]]
  private[client4] def replayableBody(a: Array[Byte]): ReplayableBody = Some(Left(a))
  private[client4] def replayableBody(f: SttpFile): ReplayableBody = Some(Right(f))
  private[client4] val nonReplayableBody: ReplayableBody = None

  private[client4] val IOBufferSize = 1024

  implicit class RichByteBuffer(byteBuffer: ByteBuffer) {
    def safeRead(): Array[Byte] = {
      val array = new Array[Byte](byteBuffer.remaining())
      byteBuffer.get(array)
      array
    }
  }
}
