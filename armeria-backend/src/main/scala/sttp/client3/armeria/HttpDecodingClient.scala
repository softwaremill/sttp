package sttp.client3.armeria

import com.linecorp.armeria.client.{ClientRequestContext, HttpClient, SimpleDecoratingHttpClient}
import com.linecorp.armeria.common._
import com.linecorp.armeria.common.encoding.{StreamDecoder, StreamDecoderFactory}
import com.linecorp.armeria.internal.common.ArmeriaHttpUtil
import com.linecorp.armeria.internal.shaded.guava.base.Ascii
import io.netty.buffer.ByteBufAllocator
import java.io.UnsupportedEncodingException
import org.reactivestreams.Subscriber
import sttp.client3.armeria.HttpDecodingClient.decoderFactories

/** An HttpClient that decodes a compressed `HttpData` */
private[armeria] final class HttpDecodingClient(delegate: HttpClient) extends SimpleDecoratingHttpClient(delegate) {

  override def execute(ctx: ClientRequestContext, req: HttpRequest): HttpResponse = {
    val acceptEncoding = req.headers().get(HttpHeaderNames.ACCEPT_ENCODING)

    if (acceptEncoding == null) {
      delegate.execute(ctx, req)
    } else {
      val encodings = acceptEncoding.split(",")
      val availableFactories = encodings.flatMap(encoding => decoderFactories.get(encoding))

      if (availableFactories.isEmpty) {
        delegate.execute(ctx, req)
      } else {
        val res = delegate.execute(ctx, req)
        new HttpDecodedResponse(res, decoderFactories, ctx.alloc)
      }
    }
  }
}

private[armeria] object HttpDecodingClient {

  val decoderFactories: Map[String, StreamDecoderFactory] =
    List(StreamDecoderFactory.gzip(), StreamDecoderFactory.deflate())
      .map(factory => (factory.encodingHeaderValue(), factory))
      .toMap
}

/** A `FilteredHttpResponse` that applies HTTP decoding to `HttpObject`s as they are published. */
private final class HttpDecodedResponse(
    delegate: HttpResponse,
    availableDecoders: Map[String, StreamDecoderFactory],
    alloc: ByteBufAllocator
) extends FilteredHttpResponse(delegate, true) {
  private var responseDecoder: Option[StreamDecoder] = None
  private var headersReceived = false

  override protected def filter(obj: HttpObject): HttpObject = {
    obj match {
      case headers: HttpHeaders =>
        // Skip informational headers.
        val status = headers.get(HttpHeaderNames.STATUS)
        if (ArmeriaHttpUtil.isInformational(status)) return obj
        if (headersReceived) {
          // Trailers, no modification.
        } else if (status == null) {
          // Follow-up headers for informational headers, no modification.
        } else {
          headersReceived = true
          val contentEncoding = headers.get(HttpHeaderNames.CONTENT_ENCODING)
          if (contentEncoding != null) {
            val decoderFactory = availableDecoders.get(Ascii.toLowerCase(contentEncoding))
            // If the server returned an encoding we don't support (shouldn't happen since we set
            // Accept-Encoding), a response should be failed.
            decoderFactory.fold {
              delegate.abort(new UnsupportedEncodingException(s"encoding: ${contentEncoding}"))
            } { factory =>
              responseDecoder = Some(factory.newDecoder(alloc))
            }
          }
        }
        headers
      case data: HttpData =>
        responseDecoder.fold(data) {
          _.decode(data)
        }
    }
  }

  override protected def beforeComplete(subscriber: Subscriber[_ >: HttpObject]): Unit = {
    responseDecoder.foreach(decoder => {
      val lastData = decoder.finish
      if (!lastData.isEmpty) subscriber.onNext(lastData)
    })
  }

  override protected def beforeError(subscriber: Subscriber[_ >: HttpObject], cause: Throwable): Throwable = {
    responseDecoder.foreach(_.finish())
    cause
  }
}
