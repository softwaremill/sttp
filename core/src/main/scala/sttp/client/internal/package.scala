package sttp.client

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.{Buffer, ByteBuffer}

import scala.annotation.{implicitNotFound, tailrec}

package object internal {
  private[client] def contentTypeWithCharset(ct: String, charset: String): String =
    s"$ct; charset=$charset"

  private[client] def charsetFromContentType(ct: String): Option[String] =
    ct.split(";").map(_.trim.toLowerCase).collectFirst {
      case s if s.startsWith("charset=") && s.substring(8).trim != "" => s.substring(8).trim
    }

  private[client] def transfer(is: InputStream, os: OutputStream): Unit = {
    var read = 0
    val buf = new Array[Byte](1024)

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

  private[client] def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    transfer(is, os)
    os.toByteArray
  }

  private[client] def emptyInputStream(): InputStream = new ByteArrayInputStream(Array[Byte]())

  private[client] def concatByteBuffers(bb1: ByteBuffer, bb2: ByteBuffer): ByteBuffer = {
    val buf = ByteBuffer
      .allocate(bb1.array().length + bb2.array().length)
      .put(bb1)
      .put(bb2)
    // rewind() returns Buffer in Java8, and ByteBuffer in Java11
    // calling the method from the base class to avoid NoSuchMethodError
    (buf: Buffer).rewind()
    buf
  }

  /**
    * Removes quotes surrounding the charset.
    */
  private[client] def sanitizeCharset(charset: String): String = {
    val c2 = charset.trim()
    val c3 = if (c2.startsWith("\"")) c2.substring(1) else c2
    if (c3.endsWith("\"")) c3.substring(0, c3.length - 1) else c3
  }

  @implicitNotFound(
    "This is a partial request, the method & url are not specified. Use " +
      ".get(...), .post(...) etc. to obtain a non-partial request."
  )
  private[client] type IsIdInRequest[U[_]] = U[Unit] =:= Identity[Unit]

  private[client] val Utf8 = "utf-8"
  private[client] val Iso88591 = "iso-8859-1"
  private[client] val CrLf = "\r\n"
}
