package sttp.client4.internal.httpclient

import sttp.client4._
import sttp.client4.internal.httpclient
import sttp.model.Part
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.client4.internal.throwNestedMultipartNotAllowed
import sttp.client4.internal.Utf8
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.net.http.HttpRequest
import java.util.function.Supplier
import java.io.File
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.io.ByteArrayInputStream
import java.util.UUID
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

trait MultipartBodyBuilder[BinaryStream, F[_]] {
  def multipartBodyPublisher(
      parts: Seq[Part[GenericRequestBody[_]]]
  )(implicit m: MonadError[F]): (F[HttpRequest.BodyPublisher], String)
}

class NonStreamMultipartBodyBuilder[BinaryStream, F[_]] extends MultipartBodyBuilder[BinaryStream, F] {
  override def multipartBodyPublisher(
      parts: Seq[Part[GenericRequestBody[_]]]
  )(implicit m: MonadError[F]): (F[HttpRequest.BodyPublisher], String) = {
    val multipartBuilder = new MultiPartBodyPublisher()
    parts.foreach { p =>
      val allHeaders = Header(HeaderNames.ContentDisposition, p.contentDispositionHeaderValue) +: p.headers
      val partHeaders = allHeaders.map(h => h.name -> h.value).toMap.asJava
      p.body match {
        case NoBody         => // ignore
        case FileBody(f, _) => multipartBuilder.addPart(p.name, f.toFile.toPath, partHeaders)
        case StringBody(b, e, _) if e.equalsIgnoreCase(Utf8) => multipartBuilder.addPart(p.name, b, partHeaders)
        case StringBody(b, e, _) =>
          multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b.getBytes(e))), partHeaders)
        case ByteArrayBody(b, _) =>
          multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b)), partHeaders)
        case ByteBufferBody(b, _) =>
          if ((b: Buffer).isReadOnly)
            multipartBuilder.addPart(p.name, supplier(new ByteBufferBackedInputStream(b)), partHeaders)
          else
            multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b.array())), partHeaders)
        case InputStreamBody(b, _) => multipartBuilder.addPart(p.name, supplier(b), partHeaders)
        case StreamBody(_) =>
          throw new IllegalArgumentException("Multipart streaming bodies are not supported with this backend")
        case m: MultipartBody[_] => throwNestedMultipartNotAllowed
      }
    }
    (multipartBuilder.build().unit, multipartBuilder.getBoundary)
  }

  private def supplier(t: => InputStream) =
    new Supplier[InputStream] {
      override def get(): InputStream = t
    }
}

trait StreamMultipartBodyBuilder[BinaryStream, F[_]] extends MultipartBodyBuilder[BinaryStream, F] {
  def fileToStream(file: File): BinaryStream

  def byteArrayToStream(array: Array[Byte]): BinaryStream

  def concatStreams(stream1: BinaryStream, stream2: BinaryStream): BinaryStream

  def toPublisher(stream: BinaryStream): F[HttpRequest.BodyPublisher]

  override def multipartBodyPublisher(
      parts: Seq[Part[GenericRequestBody[_]]]
  )(implicit m: MonadError[F]): (F[HttpRequest.BodyPublisher], String) = {
    val boundary: String = UUID.randomUUID.toString
    val resultStream = parts.foldLeft(byteArrayToStream(Array.empty[Byte])) { (accumulatedStream, part) =>
      val allHeaders = Header(HeaderNames.ContentDisposition, part.contentDispositionHeaderValue) +: part.headers
      val partHeaders = allHeaders.map(h => h.name -> h.value).toMap
      part.body match {
        case NoBody => accumulatedStream
        case FileBody(f, _) => {
          val encodedHeaders = byteArrayToStream(encodeHeaders(partHeaders, boundary))
          val endPartBytes = byteArrayToStream(CRLFBytes)
          val headersWithContent = concatStreams(encodedHeaders, fileToStream(f.toFile))
          concatStreams(headersWithContent, endPartBytes)
        }
        case StringBody(b, e, _) if e.equalsIgnoreCase(Utf8) =>
          concatBytesToStream(accumulatedStream, encodeString(b, partHeaders, boundary))
        case StringBody(b, e, _) =>
          concatBytesToStream(accumulatedStream, encodeBytes(b.getBytes(e), partHeaders, boundary))
        case ByteArrayBody(b, _) =>
          concatBytesToStream(accumulatedStream, encodeBytes(b, partHeaders, boundary))
        case ByteBufferBody(b, _) =>
          if ((b: Buffer).isReadOnly) {
            val buffer = new ByteBufferBackedInputStream(b)
            concatBytesToStream(accumulatedStream, encodeBytes(buffer.readAllBytes(), partHeaders, boundary))
          } else
            concatBytesToStream(accumulatedStream, encodeBytes(b.array(), partHeaders, boundary))
        case InputStreamBody(b, _) =>
          concatBytesToStream(accumulatedStream, encodeBytes(b.readAllBytes(), partHeaders, boundary))
        case StreamBody(s) =>
          concatStreams(
            concatBytesToStream(accumulatedStream, encodeHeaders(partHeaders, boundary)),
            concatBytesToStream(s.asInstanceOf[BinaryStream], CRLFBytes)
          )
        case _: MultipartBody[_] => throwNestedMultipartNotAllowed
      }
    }
    (toPublisher(concatBytesToStream(resultStream, lastBoundary(boundary))), boundary)
  }

  private def concatBytesToStream(stream: BinaryStream, array: Array[Byte]): BinaryStream =
    concatStreams(stream, byteArrayToStream(array))

  private def headersToString(headers: Map[String, String]): String =
    headers.map { case (k, v) => k + ": " + v }.mkString("\r\n")

  private def encodeHeaders(headers: Map[String, String], boundary: String): Array[Byte] =
    ("--" + boundary + "\r\n" + headersToString(headers) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8)

  private val CRLFBytes: Array[Byte] = "\r\n".getBytes(StandardCharsets.UTF_8)

  private def encodeString(value: String, headers: Map[String, String], boundary: String): Array[Byte] =
    encodeHeaders(headers, boundary) ++ value.getBytes(StandardCharsets.UTF_8) ++ CRLFBytes

  private def encodeBytes(value: Array[Byte], headers: Map[String, String], boundary: String): Array[Byte] = {
    encodeHeaders(headers, boundary) ++ value ++ CRLFBytes
  }

  private def lastBoundary(boundary: String): Array[Byte] = {
    val lastPart = "--" + boundary + "--"
    lastPart.getBytes(StandardCharsets.UTF_8)
  }
}

// https://stackoverflow.com/a/6603018/362531
private[httpclient] class ByteBufferBackedInputStream(buf: ByteBuffer) extends InputStream {
  override def read: Int = {
    if (!buf.hasRemaining) return -1
    buf.get & 0xff
  }

  override def read(bytes: Array[Byte], off: Int, len: Int): Int = {
    if (!buf.hasRemaining) return -1
    val len2 = Math.min(len, buf.remaining)
    buf.get(bytes, off, len2)
    len2
  }
}
