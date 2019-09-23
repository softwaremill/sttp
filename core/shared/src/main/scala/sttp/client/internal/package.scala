package sttp.client

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer

import scala.annotation.{implicitNotFound, tailrec}

package object internal {
  private[sttp] def contentTypeWithCharset(ct: String, charset: String): String =
    s"$ct; charset=$charset"

  private[sttp] def charsetFromContentType(ct: String): Option[String] =
    ct.split(";").map(_.trim.toLowerCase).collectFirst {
      case s if s.startsWith("charset=") && s.substring(8).trim != "" => s.substring(8).trim
    }

  private[sttp] def transfer(is: InputStream, os: OutputStream): Unit = {
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

  private[sttp] def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    transfer(is, os)
    os.toByteArray
  }

  private[sttp] def concatByteBuffers(bb1: ByteBuffer, bb2: ByteBuffer): ByteBuffer =
    ByteBuffer
      .allocate(bb1.array().length + bb2.array().length)
      .put(bb1)
      .put(bb2)

  @implicitNotFound(
    "This is a partial request, the method & url are not specified. Use " +
      ".get(...), .post(...) etc. to obtain a non-partial request."
  )
  private[sttp] type IsIdInRequest[U[_]] = U[Unit] =:= Identity[Unit]

  private[sttp] val Utf8 = "utf-8"
  private[sttp] val Iso88591 = "iso-8859-1"
  private[sttp] val CrLf = "\r\n"
}
