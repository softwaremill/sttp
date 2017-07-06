package com.softwaremill.sttp

import java.io.{ByteArrayOutputStream, InputStream, OutputStream, OutputStreamWriter}
import java.net.HttpURLConnection
import java.nio.channels.Channels
import java.nio.file.Files

import com.softwaremill.sttp.model._

import scala.annotation.tailrec
import scala.io.Source

object HttpConnectionSttpHandler extends SttpHandler[Id, Nothing] {
  override def send[T](r: Request, responseAs: ResponseAs[T, Nothing]): Response[T] = {
    val c = r.uri.toURL.openConnection().asInstanceOf[HttpURLConnection]
    c.setRequestMethod(r.method.m)
    r.headers.foreach { case (k, v) => c.setRequestProperty(k, v) }
    c.setDoInput(true)
    setBody(r.body, c)

    val status = c.getResponseCode
    Response(status, readResponse(c.getInputStream, responseAs))
  }

  private def setBody(body: RequestBody, c: HttpURLConnection): Unit = {
    if (body != NoBody) c.setDoOutput(true)

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

    body match {
      case NoBody => // skip

      case StringBody(b, encoding) =>
        val writer = new OutputStreamWriter(c.getOutputStream, encoding)
        try writer.write(b) finally writer.close()

      case ByteArrayBody(b) =>
        c.getOutputStream.write(b)

      case ByteBufferBody(b) =>
        val channel = Channels.newChannel(c.getOutputStream)
        try channel.write(b) finally channel.close()

      case InputStreamBody(b) =>
        copyStream(b, c.getOutputStream)

      case FileBody(b) =>
        Files.copy(b.toPath, c.getOutputStream)

      case PathBody(b) =>
        Files.copy(b, c.getOutputStream)

      case SerializableBody(f, t) =>
        setBody(f(t), c)
    }
  }

  private def readResponse[T](is: InputStream, responseAs: ResponseAs[T, Nothing]): T = responseAs match {
    case IgnoreResponse =>
      @tailrec def consume(): Unit = if (is.read() != -1) consume()
      consume()

    case ResponseAsString(enc) =>
      Source.fromInputStream(is, enc).mkString

    case ResponseAsByteArray =>
      val os = new ByteArrayOutputStream
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

      os.toByteArray

    case ResponseAsStream() =>
      // only possible when the user requests the response as a stream of Nothing. Oh well ...
      throw new IllegalStateException()
  }
}
