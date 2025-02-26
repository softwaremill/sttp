package sttp.client4.internal.httpclient

import sttp.capabilities.Streams
import sttp.client4._
import sttp.client4.compression.Compressor
import sttp.client4.httpclient.BodyProgressCallback
import sttp.client4.internal.SttpToJavaConverters.toJavaSupplier
import sttp.client4.internal.{NoStreams, Utf8, throwNestedMultipartNotAllowed}
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.Part
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import java.util.concurrent.Flow.Subscription
import java.util.function.Supplier
import scala.collection.JavaConverters._

private[client4] trait BodyToHttpClient[F[_], S, R] {
  val streams: Streams[S]
  implicit def monad: MonadError[F]

  def apply[T](
      request: GenericRequest[T, R],
      builder: HttpRequest.Builder,
      contentType: Option[String]
  ): F[BodyPublisher] = {
    val (maybeCompressedBody, contentLength) = Compressor.compressIfNeeded(request, compressors)
    val body = maybeCompressedBody match {
      case NoBody              => BodyPublishers.noBody().unit
      case StringBody(b, _, _) => BodyPublishers.ofString(b).unit
      case ByteArrayBody(b, _) => BodyPublishers.ofByteArray(b).unit
      case ByteBufferBody(b, _) =>
        if (b.hasArray) BodyPublishers.ofByteArray(b.array(), 0, b.limit()).unit
        else { val a = new Array[Byte](b.remaining()); b.get(a); BodyPublishers.ofByteArray(a).unit }
      case InputStreamBody(b, _) => BodyPublishers.ofInputStream(toJavaSupplier(() => b)).unit
      case FileBody(f, _)        => BodyPublishers.ofFile(f.toFile.toPath).unit
      case StreamBody(s)         => streamToPublisher(s.asInstanceOf[streams.BinaryStream])
      case m: MultipartBody[_] =>
        val baseContentType = contentType.getOrElse("multipart/form-data")
        if (streams == NoStreams) {
          val multipartBodyPublisher = multipartBody(m.parts)
          builder.header(HeaderNames.ContentType, s"$baseContentType; boundary=${multipartBodyPublisher.getBoundary}")
          multipartBodyPublisher.build().unit
        } else {
          val bodyBuilder = new MultipartStreamingBodyBuilder()
          builder.header(HeaderNames.ContentType, s"$baseContentType; boundary=${bodyBuilder.getBoundary}")
          multipartStreamBody(m.parts, bodyBuilder)
        }

    }

    val bodyWithContentLength = contentLength match {
      case None     => body
      case Some(cl) => body.map(b => withKnownContentLength(b, cl))
    }

    request.attribute(BodyProgressCallback.RequestAttribute) match {
      case None           => bodyWithContentLength
      case Some(callback) => bodyWithContentLength.map(withCallback(_, callback))
    }
  }

  def byteArrayToStream(array: Array[Byte]): streams.BinaryStream
  def concatStreams(stream1: streams.BinaryStream, stream2: streams.BinaryStream): streams.BinaryStream

  def streamToPublisher(stream: streams.BinaryStream): F[BodyPublisher]
  def compressors: List[Compressor[R]]

  private def multipartStreamBody(
      parts: Seq[Part[GenericRequestBody[_]]],
      bodybuilder: MultipartStreamingBodyBuilder
  ): F[HttpRequest.BodyPublisher] = {
    val resultStream = parts.foldLeft(byteArrayToStream(Array.empty[Byte])) { (accumulatedStream, part) =>
      val allHeaders = Header(HeaderNames.ContentDisposition, part.contentDispositionHeaderValue) +: part.headers
      val partHeaders = allHeaders.map(h => h.name -> h.value).toMap
      part.body match {
        case NoBody => accumulatedStream
        case FileBody(f, _) =>
          concatBytesToStream(accumulatedStream, bodybuilder.encodeFile(f.toFile.toPath, partHeaders))
        case StringBody(b, e, _) if e.equalsIgnoreCase(Utf8) =>
          concatBytesToStream(accumulatedStream, bodybuilder.encodeString(b, partHeaders))
        case StringBody(b, e, _) =>
          concatBytesToStream(accumulatedStream, bodybuilder.encodeBytes(b.getBytes(e), partHeaders))
        case ByteArrayBody(b, _) =>
          concatBytesToStream(accumulatedStream, bodybuilder.encodeBytes(b, partHeaders))
        case ByteBufferBody(b, _) =>
          if ((b: Buffer).isReadOnly) {
            val buffer = new ByteBufferBackedInputStream(b)
            concatBytesToStream(accumulatedStream, bodybuilder.encodeBytes(buffer.readAllBytes(), partHeaders))
          } else
            concatBytesToStream(accumulatedStream, bodybuilder.encodeBytes(b.array(), partHeaders))
        case InputStreamBody(b, _) =>
          concatBytesToStream(accumulatedStream, bodybuilder.encodeBytes(b.readAllBytes(), partHeaders))
        case StreamBody(s)       => concatStreams(accumulatedStream, s.asInstanceOf[streams.BinaryStream])
        case _: MultipartBody[_] => throwNestedMultipartNotAllowed
      }
    }
    streamToPublisher(concatBytesToStream(resultStream, bodybuilder.lastBoundary))
  }

  private def concatBytesToStream(stream: streams.BinaryStream, array: Array[Byte]): streams.BinaryStream =
    concatStreams(stream, byteArrayToStream(array))

  private def multipartBody(parts: Seq[Part[GenericRequestBody[_]]]) = {
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

  private def withCallback(
      delegate: HttpRequest.BodyPublisher,
      callback: BodyProgressCallback
  ): HttpRequest.BodyPublisher =
    new HttpRequest.BodyPublisher {
      override def contentLength(): Long = delegate.contentLength()
      override def subscribe(subscriber: Flow.Subscriber[_ >: ByteBuffer]): Unit = {
        delegate.subscribe(new Flow.Subscriber[ByteBuffer] {
          override def onSubscribe(subscription: Subscription): Unit = {
            runCallbackSafe {
              val cl = contentLength()
              callback.onInit(if (cl < 0) None else Some(cl))
            }
            subscriber.onSubscribe(subscription)
          }

          override def onNext(item: ByteBuffer): Unit = {
            runCallbackSafe(callback.onNext(item.remaining()))
            subscriber.onNext(item)
          }

          override def onComplete(): Unit = {
            runCallbackSafe(callback.onComplete())
            subscriber.onComplete()
          }
          override def onError(throwable: Throwable): Unit = {
            runCallbackSafe(callback.onError(throwable))
            subscriber.onError(throwable)
          }

          private def runCallbackSafe(f: => Unit): Unit =
            try f
            catch {
              case e: Exception =>
                System.getLogger(this.getClass.getName).log(System.Logger.Level.ERROR, "Error in callback", e)
            }
        })
      }
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
