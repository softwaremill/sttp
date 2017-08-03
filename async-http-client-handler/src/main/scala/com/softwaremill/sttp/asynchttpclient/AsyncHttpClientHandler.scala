package com.softwaremill.sttp.asynchttpclient

import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.softwaremill.sttp.model._
import com.softwaremill.sttp.{
  ContentLengthHeader,
  MonadAsyncError,
  MonadError,
  Request,
  Response,
  SttpHandler
}
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.{
  AsyncCompletionHandler,
  AsyncHandler,
  AsyncHttpClient,
  HttpResponseBodyPart,
  HttpResponseHeaders,
  HttpResponseStatus,
  RequestBuilder,
  Request => AsyncRequest,
  Response => AsyncResponse
}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.JavaConverters._
import scala.language.higherKinds

abstract class AsyncHttpClientHandler[R[_], S](asyncHttpClient: AsyncHttpClient,
                                               rm: MonadAsyncError[R],
                                               closeClient: Boolean)
    extends SttpHandler[R, S] {

  override def send[T](r: Request[T, S]): R[Response[T]] = {
    val preparedRequest = asyncHttpClient
      .prepareRequest(requestToAsync(r))

    rm.flatten(rm.async[R[Response[T]]] { cb =>
      def success(r: R[Response[T]]) = cb(Right(r))
      def error(t: Throwable) = cb(Left(t))

      r.responseAs match {
        case ras @ ResponseAsStream() =>
          preparedRequest
            .execute(streamingAsyncHandler(ras, success, error))

        case ra =>
          preparedRequest
            .execute(eagerAsyncHandler(ra, success, error))
      }
    })
  }

  override def responseMonad: MonadError[R] = rm

  protected def streamBodyToPublisher(s: S): Publisher[ByteBuffer]

  protected def publisherToStreamBody(p: Publisher[ByteBuffer]): S

  private def eagerAsyncHandler[T](
      responseAs: ResponseAs[T, S],
      success: R[Response[T]] => Unit,
      error: Throwable => Unit): AsyncHandler[Unit] = {

    new AsyncCompletionHandler[Unit] {
      override def onCompleted(response: AsyncResponse): Unit =
        success(readEagerResponse(response, responseAs))

      override def onThrowable(t: Throwable): Unit = error(t)
    }
  }

  private def streamingAsyncHandler[T](
      responseAs: ResponseAsStream[T, S],
      success: R[Response[T]] => Unit,
      error: Throwable => Unit): AsyncHandler[Unit] = {
    new StreamedAsyncHandler[Unit] {
      private val builder = new AsyncResponse.ResponseBuilder()
      private var publisher: Option[Publisher[ByteBuffer]] = None
      private var completed = false

      override def onStream(
          p: Publisher[HttpResponseBodyPart]): AsyncHandler.State = {
        // Sadly we don't have .map on Publisher
        publisher = Some(new Publisher[ByteBuffer] {
          override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit =
            p.subscribe(new Subscriber[HttpResponseBodyPart] {
              override def onError(t: Throwable): Unit = s.onError(t)
              override def onComplete(): Unit = s.onComplete()
              override def onNext(t: HttpResponseBodyPart): Unit =
                s.onNext(t.getBodyByteBuffer)
              override def onSubscribe(v: Subscription): Unit =
                s.onSubscribe(v)
            })
        })
        // #2: sometimes onCompleted() isn't called, only onStream(); this
        // seems to be true esp for https sites. For these cases, completing
        // the request here.
        doComplete()
        State.CONTINUE
      }

      override def onBodyPartReceived(
          bodyPart: HttpResponseBodyPart): AsyncHandler.State =
        throw new IllegalStateException(
          "Requested a streaming handler, unexpected eager body parts.")

      override def onHeadersReceived(
          headers: HttpResponseHeaders): AsyncHandler.State = {
        builder.accumulate(headers)
        State.CONTINUE
      }

      override def onStatusReceived(
          responseStatus: HttpResponseStatus): AsyncHandler.State = {
        builder.accumulate(responseStatus)
        State.CONTINUE
      }

      override def onCompleted(): Unit = {
        // if the request had no body, onStream() will never be called
        doComplete()
      }

      private def doComplete(): Unit = {
        if (!completed) {
          completed = true

          val baseResponse = readResponseNoBody(builder.build())
          val p = publisher.getOrElse(EmptyPublisher)
          val s = publisherToStreamBody(p)
          val t = responseAs.responseIsStream(s)
          success(rm.unit(baseResponse.copy(body = t)))
        }
      }

      override def onThrowable(t: Throwable): Unit = {
        error(t)
      }
    }
  }

  private def requestToAsync(r: Request[_, S]): AsyncRequest = {
    val rb = new RequestBuilder(r.method.m).setUrl(r.uri.toString)
    r.headers.foreach { case (k, v) => rb.setHeader(k, v) }
    setBody(r, r.body, rb)
    rb.build()
  }

  private def setBody(r: Request[_, S],
                      body: RequestBody[S],
                      rb: RequestBuilder): Unit = {
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
        setBody(r, f(t), rb)

      case StreamBody(s) =>
        val cl = r.headers
          .find(_._1.equalsIgnoreCase(ContentLengthHeader))
          .map(_._2.toLong)
          .getOrElse(-1L)
        rb.setBody(streamBodyToPublisher(s), cl)
    }
  }

  private def readEagerResponse[T](
      response: AsyncResponse,
      responseAs: ResponseAs[T, S]): R[Response[T]] = {
    val body = readEagerResponseBody(response, responseAs)
    rm.map(body, (b: T) => readResponseNoBody(response).copy(body = b))
  }

  private def readResponseNoBody(response: AsyncResponse): Response[Unit] = {
    Response((),
             response.getStatusCode,
             response.getHeaders
               .iterator()
               .asScala
               .map(e => (e.getKey, e.getValue))
               .toList)
  }

  private def readEagerResponseBody[T](response: AsyncResponse,
                                       responseAs: ResponseAs[T, S]): R[T] = {

    def asString(enc: String) = response.getResponseBody(Charset.forName(enc))

    responseAs match {
      case MappedResponseAs(raw, g) =>
        rm.map(readEagerResponseBody(response, raw), g)

      case IgnoreResponse =>
        // getting the body and discarding it
        response.getResponseBodyAsBytes
        rm.unit(())

      case ResponseAsString(enc) =>
        rm.unit(asString(enc))

      case ResponseAsByteArray =>
        rm.unit(response.getResponseBodyAsBytes)

      case ResponseAsStream() =>
        // only possible when the user requests the response as a stream of
        // Nothing. Oh well ...
        rm.error(
          new IllegalStateException(
            "Requested a streaming response, trying to read eagerly."))
    }
  }

  override def close(): Unit = {
    if (closeClient)
      asyncHttpClient.close()
  }
}

object EmptyPublisher extends Publisher[ByteBuffer] {
  override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit = {
    s.onComplete()
  }
}
