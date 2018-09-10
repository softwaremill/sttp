package com.softwaremill.sttp.asynchttpclient

import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.softwaremill.sttp.ResponseAs.EagerResponseHandler
import com.softwaremill.sttp.SttpBackendOptions.ProxyType.{Http, Socks}
import com.softwaremill.sttp._
import com.softwaremill.sttp.internal._
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.proxy.ProxyServer
import org.asynchttpclient.request.body.multipart.{ByteArrayPart, FilePart, StringPart}
import org.asynchttpclient.{
  AsyncCompletionHandler,
  AsyncHandler,
  AsyncHttpClient,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig,
  HttpResponseBodyPart,
  HttpResponseStatus,
  Param,
  RequestBuilder,
  Request => AsyncRequest,
  Response => AsyncResponse
}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.util.{Failure, Try}

abstract class AsyncHttpClientBackend[R[_], S](asyncHttpClient: AsyncHttpClient,
                                               rm: MonadAsyncError[R],
                                               closeClient: Boolean)
    extends SttpBackend[R, S] {

  override def send[T](r: Request[T, S]): R[Response[T]] = {
    val preparedRequest = asyncHttpClient
      .prepareRequest(requestToAsync(r))

    rm.flatten(rm.async[R[Response[T]]] { cb =>
      def success(r: R[Response[T]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      r.response match {
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

  protected def streamBodyToPublisher(s: S): Publisher[ByteBuf]

  protected def publisherToStreamBody(p: Publisher[ByteBuffer]): S

  protected def publisherToBytes(p: Publisher[ByteBuffer]): R[Array[Byte]]

  private def eagerAsyncHandler[T](responseAs: ResponseAs[T, S],
                                   success: R[Response[T]] => Unit,
                                   error: Throwable => Unit): AsyncHandler[Unit] = {

    new AsyncCompletionHandler[Unit] {
      override def onCompleted(response: AsyncResponse): Unit =
        success(readEagerResponse(response, responseAs))

      override def onThrowable(t: Throwable): Unit = error(t)
    }
  }

  private def streamingAsyncHandler[T](responseAs: ResponseAsStream[T, S],
                                       success: R[Response[T]] => Unit,
                                       error: Throwable => Unit): AsyncHandler[Unit] = {
    new StreamedAsyncHandler[Unit] {
      private val builder = new AsyncResponse.ResponseBuilder()
      private var publisher: Option[Publisher[ByteBuffer]] = None
      private var completed = false

      override def onStream(p: Publisher[HttpResponseBodyPart]): AsyncHandler.State = {
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

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State =
        throw new IllegalStateException("Requested a streaming backend, unexpected eager body parts.")

      override def onHeadersReceived(headers: HttpHeaders): AsyncHandler.State = {
        builder.accumulate(headers)
        State.CONTINUE
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State = {
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
          val b = if (StatusCodes.isSuccess(baseResponse.code)) {
            rm.unit(Right(responseAs.responseIsStream(s)))
          } else {
            rm.map(publisherToBytes(p))(Left(_))
          }

          success(rm.map(b) { bb: Either[Array[Byte], T] =>
            baseResponse.copy(rawErrorBody = bb)
          })
        }
      }

      override def onThrowable(t: Throwable): Unit = {
        error(t)
      }
    }
  }

  private def requestToAsync(r: Request[_, S]): AsyncRequest = {
    val readTimeout = r.options.readTimeout
    val rb = new RequestBuilder(r.method.m)
      .setUrl(r.uri.toString)
      .setReadTimeout(if (readTimeout.isFinite()) readTimeout.toMillis.toInt else -1)
      .setRequestTimeout(if (readTimeout.isFinite()) readTimeout.toMillis.toInt else -1)
    r.headers.foreach { case (k, v) => rb.setHeader(k, v) }
    setBody(r, r.body, rb)
    rb.build()
  }

  private def setBody(r: Request[_, S], body: RequestBody[S], rb: RequestBuilder): Unit = {
    body match {
      case NoBody => // skip

      case StringBody(b, encoding, _) =>
        rb.setBody(b.getBytes(encoding))

      case ByteArrayBody(b, _) =>
        rb.setBody(b)

      case ByteBufferBody(b, _) =>
        rb.setBody(b)

      case InputStreamBody(b, _) =>
        rb.setBody(b)

      case FileBody(b, _) =>
        rb.setBody(b.toFile)

      case StreamBody(s) =>
        val cl = r.headers
          .find(_._1.equalsIgnoreCase(HeaderNames.ContentLength))
          .map(_._2.toLong)
          .getOrElse(-1L)
        rb.setBody(streamBodyToPublisher(s), cl)

      case MultipartBody(ps) =>
        ps.foreach(addMultipartBody(rb, _))
    }
  }

  private def addMultipartBody(rb: RequestBuilder, mp: Multipart): Unit = {
    // async http client only supports setting file names on file parts. To
    // set a file name on an arbitrary part we have to use a small "work
    // around", combining the file name with the name (surrounding quotes
    // are added by ahc).
    def nameWithFilename = mp.fileName.fold(mp.name) { fn =>
      s"""${mp.name}"; filename="$fn"""
    }

    val bodyPart = mp.body match {
      case StringBody(b, encoding, _) =>
        new StringPart(nameWithFilename, b, mp.contentType.getOrElse(MediaTypes.Text), Charset.forName(encoding))
      case ByteArrayBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b)
      case ByteBufferBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b.array())
      case InputStreamBody(b, _) =>
        // sadly async http client only supports parts that are strings,
        // byte arrays or files
        new ByteArrayPart(nameWithFilename, toByteArray(b))
      case FileBody(b, _) =>
        new FilePart(mp.name, b.toFile, null, null, mp.fileName.orNull)
    }

    bodyPart.setCustomHeaders(mp.additionalHeaders.map(h => new Param(h._1, h._2)).toList.asJava)

    rb.addBodyPart(bodyPart)
  }

  private def readEagerResponse[T](response: AsyncResponse, responseAs: ResponseAs[T, S]): R[Response[T]] = {
    val base = readResponseNoBody(response)

    val body = if (StatusCodes.isSuccess(base.code)) {
      rm.map(eagerResponseHandler(response).handle(responseAs, rm))(Right(_))
    } else {
      rm.map(eagerResponseHandler(response).handle(asByteArray, rm))(Left(_))
    }

    rm.map(body) { b: Either[Array[Byte], T] =>
      base.copy(rawErrorBody = b)
    }
  }

  private def readResponseNoBody(response: AsyncResponse): Response[Unit] = {
    Response(Right(()),
             response.getStatusCode,
             response.getStatusText,
             response.getHeaders
               .iteratorAsString()
               .asScala
               .map(e => (e.getKey, e.getValue))
               .toList,
             Nil)
  }

  private def eagerResponseHandler(response: AsyncResponse) =
    new EagerResponseHandler[S] {
      override def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T] =
        bra match {
          case IgnoreResponse =>
            // getting the body and discarding it
            response.getResponseBodyAsBytes
            Try(())

          case ResponseAsString(enc) =>
            val charset = Option(response.getHeader(HeaderNames.ContentType))
              .flatMap(encodingFromContentType)
              .getOrElse(enc)
            Try(response.getResponseBody(Charset.forName(charset)))

          case ResponseAsByteArray =>
            Try(response.getResponseBodyAsBytes)

          case ResponseAsStream() =>
            Failure(new IllegalStateException("Requested a streaming response, trying to read eagerly."))

          case ResponseAsFile(file, overwrite) =>
            Try {
              val f = FileHelpers.saveFile(file.toFile, response.getResponseBodyAsStream, overwrite)
              SttpFile.fromFile(f)
            }
        }
    }

  override def close(): Unit = {
    if (closeClient)
      asyncHttpClient.close()
  }
}

object AsyncHttpClientBackend {

  private[asynchttpclient] def defaultClient(options: SttpBackendOptions): AsyncHttpClient = {

    var configBuilder = new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(options.connectionTimeout.toMillis.toInt)

    configBuilder = options.proxy match {
      case None => configBuilder
      case Some(p) =>
        val proxyType: org.asynchttpclient.proxy.ProxyType =
          p.proxyType match {
            case Socks => org.asynchttpclient.proxy.ProxyType.SOCKS_V5
            case Http  => org.asynchttpclient.proxy.ProxyType.HTTP
          }

        configBuilder.setProxyServer(
          new ProxyServer.Builder(p.host, p.port)
            .setProxyType(proxyType) // Fix issue #145
            .setNonProxyHosts(p.nonProxyHosts.asJava)
            .build())
    }

    new DefaultAsyncHttpClient(configBuilder.build())
  }
}

object EmptyPublisher extends Publisher[ByteBuffer] {
  override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit = {
    s.onComplete()
  }
}
