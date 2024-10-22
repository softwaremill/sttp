package sttp.client4.internal.httpclient

import sttp.capabilities.Streams
import sttp.client4.internal.SttpToJavaConverters.toJavaSupplier
import sttp.client4.internal.{throwNestedMultipartNotAllowed, ContentEncoding, Utf8}
import sttp.client4._
import sttp.client4.internal.encoders.ContentCodec
import sttp.model.{Header, HeaderNames, Part}
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.{ByteArrayInputStream, InputStream}
import java.net.http.HttpRequest
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.nio.{Buffer, ByteBuffer}
import java.util.concurrent.Flow
import java.util.function.Supplier
import scala.collection.JavaConverters._

private[client4] trait BodyToHttpClient[F[_], S] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]

  def apply[T](
      request: GenericRequest[T, _],
      builder: HttpRequest.Builder,
      contentType: Option[String]
  ): F[BodyPublisher] = {
    val body: F[BodyPublisher] = request.options.encoding -> request.body match {
      case (_, NoBody)                => BodyPublishers.noBody().unit
      case (Nil, StringBody(b, _, _)) => BodyPublishers.ofString(b).unit
      case (Nil, ByteArrayBody(b, _)) => BodyPublishers.ofByteArray(b).unit
      case (Nil, ByteBufferBody(b, _)) =>
        if (b.hasArray) BodyPublishers.ofByteArray(b.array(), 0, b.limit()).unit
        else { val a = new Array[Byte](b.remaining()); b.get(a); BodyPublishers.ofByteArray(a).unit }
      case (Nil, InputStreamBody(b, _)) => BodyPublishers.ofInputStream(toJavaSupplier(() => b)).unit
      case (Nil, FileBody(f, _))        => BodyPublishers.ofFile(f.toFile.toPath).unit
      case (_, StreamBody(s))           => streamToPublisher(s.asInstanceOf[streams.BinaryStream])
      case (_, m: MultipartBody[_]) =>
        val multipartBodyPublisher = multipartBody(m.parts)
        val baseContentType = contentType.getOrElse("multipart/form-data")
        builder.header(HeaderNames.ContentType, s"$baseContentType; boundary=${multipartBodyPublisher.getBoundary}")
        multipartBodyPublisher.build().unit

      case (coders, r: BasicBodyPart) =>
        ContentCodec.encode(r, coders) match {
          case Left(err) => monad.error(err)
          case Right(newBody) =>
            val (body, length) = newBody
            val newRequest = request
              .contentLength(length.toLong)
              .body(body)
              .withOptions(request.options.copy(encoding = Nil))
            apply[T](newRequest, builder, contentType) // can we avoid recursion?
        }
    }

    (request.contentLength: Option[Long]) match {
      case None => body
      case Some(cl) =>
        body.map { b =>
          if (b.contentLength() >= 0) // see BodyPublisher.contentLength docs
            withKnownContentLength(b, b.contentLength())
          else
            withKnownContentLength(b, cl)
        }
    }
  }

  def streamToPublisher(stream: streams.BinaryStream): F[BodyPublisher]

  private def multipartBody[T](parts: Seq[Part[GenericRequestBody[_]]]) = {
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
          if ((b: Buffer).isReadOnly())
            multipartBuilder.addPart(p.name, supplier(new ByteBufferBackedInputStream(b)), partHeaders)
          else
            multipartBuilder.addPart(p.name, supplier(new ByteArrayInputStream(b.array())), partHeaders)
        case InputStreamBody(b, _) => multipartBuilder.addPart(p.name, supplier(b), partHeaders)
        case StreamBody(_)         => throw new IllegalArgumentException("Streaming multipart bodies are not supported")
        case m: MultipartBody[_]   => throwNestedMultipartNotAllowed
      }
    }
    multipartBuilder
  }

  private def supplier(t: => InputStream) =
    new Supplier[InputStream] {
      override def get(): InputStream = t
    }

  private def withKnownContentLength(delegate: HttpRequest.BodyPublisher, cl: Long): HttpRequest.BodyPublisher =
    new HttpRequest.BodyPublisher {
      override def contentLength(): Long = cl
      override def subscribe(subscriber: Flow.Subscriber[_ >: ByteBuffer]): Unit = delegate.subscribe(subscriber)
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
