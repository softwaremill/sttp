package com.softwaremill.sttp

import java.io.{InputStream, OutputStream, OutputStreamWriter}
import java.net.HttpURLConnection
import java.nio.channels.Channels
import java.nio.file.Files

import scala.annotation.tailrec

class HttpConnectionSttpHandler extends SttpHandler[Id] {
  override def send[T](r: Request, responseReader: ResponseBodyReader[T]): Response[T] = {
    val c = r.uri.toURL.openConnection().asInstanceOf[HttpURLConnection]
    c.setRequestMethod(r.method.m)
    r.headers.foreach { case (k, v) => c.setRequestProperty(k, v) }
    c.setDoInput(true)
    setBody(r, c)

    val status = c.getResponseCode
    Response(status, responseReader.fromInputStream(c.getInputStream))
  }

  private def setBody(r: Request, c: HttpURLConnection): Unit = {
    if (r.body != NoBody) c.setDoOutput(true)

    def copyStream(in: InputStream, out: OutputStream): Unit = {
      val buf = new Array[Byte](1024)

      @tailrec
      def doCopy(): Unit = {
        val read = in.read(buf)
        if (read != -1) {
          out.write(buf, 0, read)
          doCopy()
        }
      }

      doCopy()
    }

    r.body match {
      case NoBody => // skip

      case StringBody(b) =>
        val writer = new OutputStreamWriter(c.getOutputStream)
        try writer.write(b) finally writer.close()

      case ByteArrayBody(b) =>
        c.getOutputStream.write(b)

      case ByteBufferBody(b) =>
        val channel = Channels.newChannel(c.getOutputStream)
        try channel.write(b) finally channel.close()

      case InputStreamBody(b) =>
        copyStream(b, c.getOutputStream)

      case InputStreamSupplierBody(b) =>
        copyStream(b(), c.getOutputStream)

      case FileBody(b) =>
        Files.copy(b.toPath, c.getOutputStream)

      case PathBody(b) =>
        Files.copy(b, c.getOutputStream)
    }
  }
}
