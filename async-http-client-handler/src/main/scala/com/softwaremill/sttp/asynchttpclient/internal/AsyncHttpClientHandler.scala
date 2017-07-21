package com.softwaremill.sttp.asynchttpclient.internal

import java.nio.charset.Charset

import com.softwaremill.sttp.model._
import com.softwaremill.sttp.{Request, Response, SttpHandler}
import org.asynchttpclient.{
  AsyncCompletionHandler,
  AsyncHttpClient,
  RequestBuilder,
  Request => AsyncRequest,
  Response => AsyncResponse
}

import scala.collection.JavaConverters._
import scala.language.higherKinds

private[asynchttpclient] class AsyncHttpClientHandler[R[_]](
    asyncHttpClient: AsyncHttpClient,
    wrapper: WrapperFromAsync[R])
    extends SttpHandler[R, Nothing] {

  override def send[T](r: Request[T, Nothing]): R[Response[T]] = {
    wrapper { cb =>
      asyncHttpClient
        .prepareRequest(requestToAsync(r))
        .execute(new AsyncCompletionHandler[AsyncResponse] {
          override def onCompleted(response: AsyncResponse): AsyncResponse = {
            cb(Right(readResponse(response, r.responseAs)))
            response
          }
          override def onThrowable(t: Throwable): Unit = cb(Left(t))
        })
    }
  }

  private def requestToAsync(r: Request[_, Nothing]): AsyncRequest = {
    val rb = new RequestBuilder(r.method.m).setUrl(r.uri.toString)
    r.headers.foreach { case (k, v) => rb.setHeader(k, v) }
    setBody(r.body, rb)
    rb.build()
  }

  private def setBody(body: RequestBody[Nothing], rb: RequestBuilder): Unit = {
    body match {
      case NoBody => // skip

      case StringBody(b, encoding) =>
        rb.setBody(b.getBytes(encoding))

      case ByteArrayBody(b) =>
        rb.setBody(b)

      case ByteBufferBody(b) =>
        rb.setBody(b)

      case InputStreamBody(b) =>
        rb.setBody(b)

      case PathBody(b) =>
        rb.setBody(b.toFile)

      case SerializableBody(f, t) =>
        setBody(f(t), rb)

      case StreamBody(s) =>
        // we have an instance of nothing - everything's possible!
        s
    }
  }

  private def readResponse[T](
      response: AsyncResponse,
      responseAs: ResponseAs[T, Nothing]): Response[T] = {
    Response(readResponseBody(response, responseAs),
             response.getStatusCode,
             response.getHeaders
               .iterator()
               .asScala
               .map(e => (e.getKey, e.getValue))
               .toList)
  }

  private def readResponseBody[T](response: AsyncResponse,
                                  responseAs: ResponseAs[T, Nothing]): T = {

    def asString(enc: String) = response.getResponseBody(Charset.forName(enc))

    responseAs match {
      case IgnoreResponse(g) =>
        // getting the body and discarding it
        response.getResponseBodyAsBytes
        g(())

      case ResponseAsString(enc, g) =>
        g(asString(enc))

      case ResponseAsByteArray(g) =>
        g(response.getResponseBodyAsBytes)

      case r @ ResponseAsParams(enc, g) =>
        g(r.parse(asString(enc)))

      case ResponseAsStream(_) =>
        // only possible when the user requests the response as a stream of
        // Nothing. Oh well ...
        throw new IllegalStateException()
    }
  }
}

private[asynchttpclient] trait WrapperFromAsync[R[_]] {
  def apply[T](register: (Either[Throwable, T] => Unit) => Unit): R[T]
}
