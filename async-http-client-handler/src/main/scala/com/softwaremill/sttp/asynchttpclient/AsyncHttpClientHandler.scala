package com.softwaremill.sttp.asynchttpclient

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

class AsyncHttpClientHandler[R[_]](asyncHttpClient: AsyncHttpClient,
                                   rm: MonadAsyncError[R])
    extends SttpHandler[R, Nothing] {

  override def send[T](r: Request[T, Nothing]): R[Response[T]] = {
    rm.flatten(rm.async[R[Response[T]]] { cb =>
      asyncHttpClient
        .prepareRequest(requestToAsync(r))
        .execute(new AsyncCompletionHandler[AsyncResponse] {
          override def onCompleted(response: AsyncResponse): AsyncResponse = {
            cb(Right(readResponse(response, r.responseAs)))
            response
          }
          override def onThrowable(t: Throwable): Unit = cb(Left(t))
        })
    })
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
      responseAs: ResponseAs[T, Nothing]): R[Response[T]] = {
    val body = readResponseBody(response, responseAs)
    rm.map(body,
           Response(_: T,
                    response.getStatusCode,
                    response.getHeaders
                      .iterator()
                      .asScala
                      .map(e => (e.getKey, e.getValue))
                      .toList))
  }

  private def readResponseBody[T](response: AsyncResponse,
                                  responseAs: ResponseAs[T, Nothing]): R[T] = {

    def asString(enc: String) = response.getResponseBody(Charset.forName(enc))

    responseAs match {
      case MappedResponseAs(raw, g) =>
        rm.map(readResponseBody(response, raw), g)

      case IgnoreResponse =>
        // getting the body and discarding it
        response.getResponseBodyAsBytes
        rm.unit(())

      case ResponseAsString(enc) =>
        rm.unit(asString(enc))

      case ResponseAsByteArray =>
        rm.unit(response.getResponseBodyAsBytes)

      case r @ ResponseAsParams(enc) =>
        rm.unit(r.parse(asString(enc)))

      case ResponseAsStream() =>
        // only possible when the user requests the response as a stream of
        // Nothing. Oh well ...
        rm.error(new IllegalStateException())
    }
  }
}

trait MonadAsyncError[R[_]] {
  def unit[T](t: T): R[T]
  def map[T, T2](fa: R[T], f: T => T2): R[T2]
  def flatMap[T, T2](fa: R[T], f: T => R[T2]): R[T2]
  def async[T](register: (Either[Throwable, T] => Unit) => Unit): R[T]
  def error[T](t: Throwable): R[T]

  def flatten[T](ffa: R[R[T]]): R[T] = flatMap[R[T], T](ffa, identity)
}
