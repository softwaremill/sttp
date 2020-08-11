package sttp.client.httpclient

import java.io.InputStream
import java.net.http.HttpRequest
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.nio.{Buffer, ByteBuffer}

import sttp.client.{
  BasicRequestBody,
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  StreamBody,
  Streams,
  StringBody
}
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.model.{Header, HeaderNames, Part}

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
        if ((b: Buffer).isReadOnly()) BodyPublishers.ofInputStream(() => new ByteBufferBackedInputStream(b)).unit
        else BodyPublishers.ofByteArray(b.array()).unit
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

  private def multipartBody[T](parts: Seq[Part[BasicRequestBody]]) = {
    val multipartBuilder = new MultiPartBodyPublisher()
    parts.foreach { p =>
      val allHeaders = p.headers :+ Header(HeaderNames.ContentDisposition, p.contentDispositionHeaderValue)
      p.body match {
        case FileBody(f, _) =>
          multipartBuilder.addPart(p.name, f.toFile.toPath, allHeaders.map(h => h.name -> h.value).toMap.asJava)
        case StringBody(b, _, _) =>
          multipartBuilder.addPart(p.name, b, allHeaders.map(h => h.name -> h.value).toMap.asJava)
      }
    }
    multipartBuilder
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
