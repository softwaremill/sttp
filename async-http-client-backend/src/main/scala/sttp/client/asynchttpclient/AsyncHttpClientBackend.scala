package sttp.client.asynchttpclient

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import com.github.ghik.silencer.silent
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.proxy.ProxyServer
import org.asynchttpclient.request.body.multipart.{ByteArrayPart, FilePart, StringPart}
import org.asynchttpclient.ws.{WebSocket, WebSocketListener, WebSocketUpgradeHandler}
import org.asynchttpclient.{
  AsyncHandler,
  AsyncHttpClient,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig,
  HttpResponseBodyPart,
  HttpResponseStatus,
  Param,
  Realm,
  RequestBuilder,
  Request => AsyncRequest,
  Response => AsyncResponse
}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.client
import sttp.client.SttpBackendOptions.ProxyType.{Http, Socks}
import sttp.client.internal._
import sttp.client.monad.{Canceler, MonadAsyncError, MonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  IgnoreResponse,
  InputStreamBody,
  MappedResponseAs,
  MultipartBody,
  NoBody,
  RequestBody,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsStream,
  ResponseMetadata,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpBackendOptions,
  _
}
import sttp.model._
import sttp.client.monad.syntax._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.language.higherKinds
import scala.util.Try

abstract class AsyncHttpClientBackend[F[_], S](
    asyncHttpClient: AsyncHttpClient,
    private implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends SttpBackend[F, S, WebSocketHandler] {
  override def send[T](r: Request[T, S]): F[Response[T]] = adjustExceptions {
    preparedRequest(r).flatMap { ahcRequest =>
      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))

        val lf = ahcRequest.execute(streamingAsyncHandler(r.response, success, error))
        Canceler(() => lf.abort(new InterruptedException))
      })
    }
  }

  override def openWebsocket[T, WS_RESULT](
      r: Request[T, S],
      handler: WebSocketHandler[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = adjustExceptions {
    preparedRequest(r).flatMap { ahcRequest =>
      monad.async[WebSocketResponse[WS_RESULT]] { cb =>
        val initListener =
          new WebSocketInitListener(
            (r: WebSocketResponse[WS_RESULT]) => cb(Right(r)),
            t => cb(Left(t)),
            handler.createResult
          )
        val h = new WebSocketUpgradeHandler.Builder()
          .addWebSocketListener(initListener)
          .addWebSocketListener(handler.listener)
          .build()

        val lf = ahcRequest.execute(h)

        Canceler(() => lf.abort(new InterruptedException))
      }
    }
  }

  override def responseMonad: MonadError[F] = monad

  protected def streamBodyToPublisher(s: S): Publisher[ByteBuf]

  protected def publisherToStreamBody(p: Publisher[ByteBuffer]): S

  protected def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] = {
    monad.async { cb =>
      def success(r: ByteBuffer): Unit = cb(Right(r.array()))
      def error(t: Throwable): Unit = cb(Left(t))

      val subscriber = new SimpleSubscriber(success, error)
      p.subscribe(subscriber)

      Canceler(() => subscriber.cancel())
    }
  }

  protected def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
    publisherToBytes(p).map(bytes => FileHelpers.saveFile(f, new ByteArrayInputStream(bytes)))
  }

  private def streamingAsyncHandler[T](
      responseAs: ResponseAs[T, S],
      success: F[Response[T]] => Unit,
      error: Throwable => Unit
  ): AsyncHandler[Unit] = {
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
          val b = handleBody(p, responseAs, baseResponse)

          success(b.map(t => baseResponse.copy(body = t)))
        }
      }

      private def handleBody[TT](
          p: Publisher[ByteBuffer],
          r: ResponseAs[TT, _],
          responseMetadata: ResponseMetadata
      ): F[TT] =
        r match {
          case MappedResponseAs(raw, g) =>
            val nested = handleBody(p, raw, responseMetadata)
            nested.map(g(_, responseMetadata))
          case ResponseAsFromMetadata(f) => handleBody(p, f(responseMetadata), responseMetadata)
          case _: ResponseAsStream[_, _] => monad.unit(publisherToStreamBody(p).asInstanceOf[TT])
          case IgnoreResponse            =>
            // getting the body and discarding it
            publisherToBytes(p).map(_ => ())

          case ResponseAsByteArray =>
            publisherToBytes(p).map(b => b) // adjusting type because ResponseAs is covariant

          case ResponseAsFile(file) =>
            publisherToFile(p, file.toFile).map(_ => file)
        }

      override def onThrowable(t: Throwable): Unit = {
        error(t)
      }
    }
  }

  private def preparedRequest(r: Request[_, S]): F[BoundRequestBuilder] = {
    monad.fromTry(Try(asyncHttpClient.prepareRequest(requestToAsync(r)))).map(customizeRequest)
  }

  private def requestToAsync(r: Request[_, S]): AsyncRequest = {
    val readTimeout = r.options.readTimeout
    val rb = new RequestBuilder(r.method.method)
      .setUrl(r.uri.toString)
      .setReadTimeout(if (readTimeout.isFinite) readTimeout.toMillis.toInt else -1)
      .setRequestTimeout(if (readTimeout.isFinite) readTimeout.toMillis.toInt else -1)
    r.headers.foreach { case Header(k, v) => rb.setHeader(k, v) }
    setBody(r, r.body, rb)
    rb.build()
  }

  @silent("discarded")
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
          .find(_.is(HeaderNames.ContentLength))
          .map(_.value.toLong)
          .getOrElse(-1L)
        rb.setBody(streamBodyToPublisher(s), cl)

      case MultipartBody(ps) =>
        ps.foreach(addMultipartBody(rb, _))
    }
  }

  @silent("discarded")
  private def addMultipartBody(rb: RequestBuilder, mp: Part[BasicRequestBody]): Unit = {
    // async http client only supports setting file names on file parts. To
    // set a file name on an arbitrary part we have to use a small "work
    // around", combining the file name with the name (surrounding quotes
    // are added by ahc).
    def nameWithFilename = mp.fileName.fold(mp.name) { fn => s"""${mp.name}"; ${Part.FileNameDispositionParam}="$fn""" }

    val ctOrNull = mp.contentType.orNull

    val bodyPart = mp.body match {
      case StringBody(b, encoding, _) =>
        new StringPart(
          nameWithFilename,
          b,
          mp.contentType.getOrElse(MediaType.TextPlain.toString),
          Charset.forName(encoding)
        )
      case ByteArrayBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b, ctOrNull)
      case ByteBufferBody(b, _) =>
        new ByteArrayPart(nameWithFilename, b.array(), ctOrNull)
      case InputStreamBody(b, _) =>
        // sadly async http client only supports parts that are strings,
        // byte arrays or files
        new ByteArrayPart(nameWithFilename, toByteArray(b), ctOrNull)
      case FileBody(b, _) =>
        new FilePart(mp.name, b.toFile, ctOrNull, null, mp.fileName.orNull)
    }

    bodyPart.setCustomHeaders(
      mp.headers.filterNot(_.is(HeaderNames.ContentType)).map(h => new Param(h.name, h.value)).toList.asJava
    )

    rb.addBodyPart(bodyPart)
  }

  private def readResponseNoBody(response: AsyncResponse): Response[Unit] = {
    client.Response(
      (),
      StatusCode.unsafeApply(response.getStatusCode),
      response.getStatusText,
      readHeaders(response.getHeaders),
      Nil
    )
  }

  private def readHeaders(h: HttpHeaders): Seq[Header] =
    h.iteratorAsString()
      .asScala
      .map(e => Header.notValidated(e.getKey, e.getValue))
      .toList

  override def close(): F[Unit] = {
    if (closeClient) monad.eval(asyncHttpClient.close()) else monad.unit(())
  }

  private class WebSocketInitListener[WS_RESULT](
      _onSuccess: WebSocketResponse[WS_RESULT] => Unit,
      _onError: Throwable => Unit,
      createResult: WebSocket => WS_RESULT
  ) extends WebSocketListener {
    override def onOpen(webSocket: WebSocket): Unit = {
      webSocket.removeWebSocketListener(this)
      _onSuccess(
        client.ws.WebSocketResponse(Headers(readHeaders(webSocket.getUpgradeHeaders)), createResult(webSocket))
      )
    }

    override def onClose(webSocket: WebSocket, code: Int, reason: String): Unit = {
      throw new IllegalStateException("Should never be called, as the listener should be removed after onOpen")
    }

    override def onError(t: Throwable): Unit = _onError(t)
  }

  private def adjustExceptions[T](t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(SttpClientException.defaultExceptionToSttpClientException)
}

object AsyncHttpClientBackend {
  private[asynchttpclient] def defaultConfigBuilder(
      options: SttpBackendOptions
  ): DefaultAsyncHttpClientConfig.Builder = {
    val configBuilder = new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(options.connectionTimeout.toMillis.toInt)
      .setCookieStore(null)

    options.proxy match {
      case None => configBuilder
      case Some(p) =>
        val proxyType: org.asynchttpclient.proxy.ProxyType =
          p.proxyType match {
            case Socks => org.asynchttpclient.proxy.ProxyType.SOCKS_V5
            case Http  => org.asynchttpclient.proxy.ProxyType.HTTP
          }

        configBuilder.setProxyServer {
          val builder = new ProxyServer.Builder(p.host, p.port)
            .setProxyType(proxyType) // Fix issue #145
            .setNonProxyHosts(p.nonProxyHosts.asJava)

          p.auth.foreach { proxyAuth =>
            builder.setRealm(
              new Realm.Builder(proxyAuth.username, proxyAuth.password).setScheme(Realm.AuthScheme.BASIC)
            )
          }

          builder.build()
        }
    }
  }

  private[asynchttpclient] def defaultClient(options: SttpBackendOptions): AsyncHttpClient = {
    new DefaultAsyncHttpClient(defaultConfigBuilder(options).build())
  }

  private[asynchttpclient] def clientWithModifiedOptions(
      options: SttpBackendOptions,
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder
  ): AsyncHttpClient = {
    new DefaultAsyncHttpClient(updateConfig(defaultConfigBuilder(options)).build())
  }
}

private[asynchttpclient] object EmptyPublisher extends Publisher[ByteBuffer] {
  override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit = {
    s.onComplete()
  }
}

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
private[asynchttpclient] class SimpleSubscriber(success: ByteBuffer => Unit, error: Throwable => Unit)
    extends Subscriber[ByteBuffer] {
  // a pair of values: (is cancelled, current subscription)
  private val subscription = new AtomicReference[(Boolean, Subscription)]((false, null))
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0

  override def onSubscribe(s: Subscription): Unit = {
    assert(s != null)

    // The following can be safely run multiple times, as cancel() is idempotent
    val result = subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
        if (current._2 != null) {
          current._2.cancel() // Cancel the additional subscription
        }

        if (current._1) { // already cancelled
          s.cancel()
          (true, null)
        } else { // happy path
          (false, s)
        }
      }
    })

    if (result._2 != null) {
      result._2.request(Long.MaxValue) // not cancelled, we can request data
    }
  }

  @silent("discarded")
  override def onNext(b: ByteBuffer): Unit = {
    assert(b != null)
    val a = b.array()
    size += a.length
    chunks.add(a)
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.clear()
    error(t)
  }

  override def onComplete(): Unit = {
    val result = ByteBuffer.allocate(size)
    chunks.asScala.foreach(result.put)
    chunks.clear()
    success(result)
  }

  def cancel(): Unit = {
    // subscription.cancel is idempotent:
    // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification
    // so the following can be safely retried
    subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        if (current._2 != null) current._2.cancel()
        (true, null)
      }
    })
  }
}
