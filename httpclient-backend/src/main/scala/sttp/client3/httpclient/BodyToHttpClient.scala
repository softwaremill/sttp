package sttp.client3.httpclient

import sttp.capabilities.Streams
import sttp.client3.internal.{Utf8, throwNestedMultipartNotAllowed}
import sttp.client3.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  RequestBody,
  StreamBody,
  StringBody
}
import sttp.model.{Header, HeaderNames, Part}
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.{ByteArrayInputStream, InputStream}
import java.net.http.HttpRequest
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.nio.{Buffer, ByteBuffer}
import java.util.function.Supplier
import scala.collection.JavaConverters._

private[httpclient] trait BodyToHttpClient[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]

  def apply[T, R](
      request: Request[T, R],
      builder: HttpRequest.Builder,
      contentType: Option[String]
  ): F[BodyPublisher] = {
    request.body match {
      case NoBody              => BodyPublishers.noBody().unit
      case StringBody(b, _, _) => BodyPublishers.ofString(b).unit
      case ByteArrayBody(b, _) => BodyPublishers.ofByteArray(b).unit
      case ByteBufferBody(b, _) =>
        if (b.hasArray) BodyPublishers.ofByteArray(b.array(), 0, b.limit()).unit
        else { val a = new Array[Byte](b.remaining()); b.get(a); BodyPublishers.ofByteArray(a).unit }
      case InputStreamBody(b, _) => BodyPublishers.ofInputStream(() => b).unit
      case FileBody(f, _)        => BodyPublishers.ofFile(f.toFile.toPath).unit
      case StreamBody(s)         => streamToPublisher(s.asInstanceOf[streams.BinaryStream])
      case MultipartBody(parts) =>
        val multipartBodyPublisher = multipartBody(parts)
        val baseContentType = contentType.getOrElse("multipart/form-data")
        builder.header(HeaderNames.ContentType, s"$baseContentType; boundary=${multipartBodyPublisher.getBoundary}")
        multipartBodyPublisher.build().unit
    }
  }

  def streamToPublisher(stream: streams.BinaryStream): F[BodyPublisher]

  private def multipartBody[T](parts: Seq[Part[RequestBody[_]]]) = {
    val multipartBuilder = new MultiPartBodyPublisher()
    parts.foreach { p =>
      val allHeaders = Header(HeaderNames.ContentDisposition, p.contentDispositionHeaderValue) +: p.headers
      val partHeaders = allHeaders.map(h => h.name -> h.value).toMap.asJava
      p.body match {
        case NoBody                                          => // ignore
        case FileBody(f, _)                                  => multipartBuilder.addPart(p.name, f.toFile.toPath, partHeaders)
        case StringBody(b, e, _) if e.equalsIgnoreCase(Utf8) => multipartBuilder.addPart(p.name, b, partHeaders)
        case StringBody(b, e, _) =>
          multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b.getBytes(e))), partHeaders)
        case ByteArrayBody(b, _) =>
          multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b)), partHeaders)
        case ByteBufferBody(b, _) =>
          if ((b: Buffer).isReadOnly())
            multipartBuilder.addPart(p.name, supplier(new ByteBufferBackedInputStream(b)), partHeaders)
          else
            multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b.array())), partHeaders)
        case InputStreamBody(b, _) => multipartBuilder.addPart(p.name, supplier(b), partHeaders)
        case StreamBody(_)         => throw new IllegalArgumentException("Streaming multipart bodies are not supported")
        case MultipartBody(_)      => throwNestedMultipartNotAllowed
      }
    }
    multipartBuilder
  }

  private def supplier(t: => InputStream) =
    new Supplier[InputStream] {
      override def get(): InputStream = t
    }

  // https://stackoverflow.com/a/6603018/362531
  private class ByteBufferBackedInputStream(buf: ByteBuffer) extends InputStream {
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
}
