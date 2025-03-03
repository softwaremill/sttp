package sttp.client4.internal.httpclient

import sttp.capabilities.Streams
import sttp.client4._
import sttp.client4.compression.Compressor
import sttp.client4.httpclient.BodyProgressCallback
import sttp.client4.internal.SttpToJavaConverters.toJavaSupplier
import sttp.model.HeaderNames
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import java.util.concurrent.Flow.Subscription

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
        val (body, boundary) = multiPartBodyBuilder.multipartBodyPublisher(m.parts)(monad)
        builder.header(HeaderNames.ContentType, s"$baseContentType; boundary=$boundary")
        body
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

  def multiPartBodyBuilder: MultipartBodyBuilder[streams.BinaryStream, F]
  def streamToPublisher(stream: streams.BinaryStream): F[BodyPublisher]
  def compressors: List[Compressor[R]]

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
}
