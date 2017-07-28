package com.softwaremill.sttp

import java.io._
import java.net.{HttpURLConnection, URL}
import java.nio.channels.Channels
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import com.softwaremill.sttp.model._

import scala.annotation.tailrec
import scala.io.Source
import scala.collection.JavaConverters._

object HttpURLConnectionSttpHandler extends SttpHandler[Id, Nothing] {
  override def send[T](r: Request[T, Nothing]): Response[T] = {
    val c =
      new URL(r.uri.toString).openConnection().asInstanceOf[HttpURLConnection]
    c.setRequestMethod(r.method.m)
    r.headers.foreach { case (k, v) => c.setRequestProperty(k, v) }
    c.setDoInput(true)
    setBody(r.body, c)

    try {
      val is = c.getInputStream
      readResponse(c, is, r.responseAs)
    } catch {
      case e: CharacterCodingException     => throw e
      case e: UnsupportedEncodingException => throw e
      case _: IOException if c.getResponseCode != -1 =>
        readResponse(c, c.getErrorStream, r.responseAs)
    }
  }

  private def setBody(body: RequestBody[Nothing], c: HttpURLConnection): Unit = {
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
        try writer.write(b)
        finally writer.close()

      case ByteArrayBody(b) =>
        c.getOutputStream.write(b)

      case ByteBufferBody(b) =>
        val channel = Channels.newChannel(c.getOutputStream)
        try channel.write(b)
        finally channel.close()

      case InputStreamBody(b) =>
        copyStream(b, c.getOutputStream)

      case PathBody(b) =>
        Files.copy(b, c.getOutputStream)

      case SerializableBody(f, t) =>
        setBody(f(t), c)

      case StreamBody(s) =>
        // we have an instance of nothing - everything's possible!
        s
    }
  }

  private def readResponse[T](
      c: HttpURLConnection,
      is: InputStream,
      responseAs: ResponseAs[T, Nothing]): Response[T] = {

    val headers = c.getHeaderFields.asScala.toVector
      .filter(_._1 != null)
      .flatMap { case (k, vv) => vv.asScala.map((k, _)) }
    val contentEncoding = Option(c.getHeaderField(ContentEncodingHeader))
    Response(readResponseBody(wrapInput(contentEncoding, is), responseAs),
             c.getResponseCode,
             headers)
  }

  private def readResponseBody[T](is: InputStream,
                                  responseAs: ResponseAs[T, Nothing]): T = {

    def asString(enc: String) = Source.fromInputStream(is, enc).mkString

    responseAs match {
      case MappedResponseAs(raw, g) => g(readResponseBody(is, raw))

      case IgnoreResponse =>
        @tailrec def consume(): Unit = if (is.read() != -1) consume()
        consume()

      case ResponseAsString(enc) =>
        asString(enc)

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

      case r @ ResponseAsParams(enc) =>
        r.parse(asString(enc))

      case ResponseAsStream() =>
        // only possible when the user requests the response as a stream of
        // Nothing. Oh well ...
        throw new IllegalStateException()
    }
  }

  private def wrapInput(contentEncoding: Option[String],
                        is: InputStream): InputStream =
    contentEncoding.map(_.toLowerCase) match {
      case None            => is
      case Some("gzip")    => new GZIPInputStream(is)
      case Some("deflate") => new InflaterInputStream(is)
      case Some(ce) =>
        throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
    }
}
