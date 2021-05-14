package sttp.client3.asynchttpclient

import java.nio.ByteBuffer

import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.proxy.ProxyServer
import org.asynchttpclient.ws.{WebSocketListener, WebSocketUpgradeHandler, WebSocket => AHCWebSocket}
import org.asynchttpclient.{
  AsyncHandler,
  AsyncHttpClient,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig,
  HttpResponseBodyPart,
  HttpResponseStatus,
  Realm,
  RequestBuilder,
  Request => AsyncRequest,
  Response => AsyncResponse
}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.capabilities.{Effect, Streams}
import sttp.client3
import sttp.client3.SttpBackendOptions.ProxyType.{Http, Socks}
import sttp.client3.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError, MonadError}
import sttp.client3.{Response, SttpBackend, SttpBackendOptions, _}
import sttp.model._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.util.Try

abstract class AsyncHttpClientBackend[F[_], S <: Streams[S], P](
    asyncHttpClient: AsyncHttpClient,
    private implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends SttpBackend[F, P] {

  val streams: Streams[S]
  type PE = P with Effect[F]

  override def send[T, R >: PE](r: Request[T, R]): F[Response[T]] =
    adjustExceptions(r) {
      preparedRequest(r).flatMap { ahcRequest =>
        if (r.isWebSocket) sendWebSocket(r, ahcRequest) else sendRegular(r, ahcRequest)
      }
    }

  private def sendRegular[T, R >: PE](r: Request[T, R], ahcRequest: BoundRequestBuilder): F[Response[T]] = {
    monad.flatten(monad.async[F[Response[T]]] { cb =>
      def success(r: F[Response[T]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      val lf = ahcRequest.execute(streamingAsyncHandler(r, success, error))
      Canceler(() => lf.abort(new InterruptedException))
    })
  }

  private def sendWebSocket[T, R >: PE](
      r: Request[T, R],
      ahcRequest: BoundRequestBuilder
  ): F[Response[T]] =
    createSimpleQueue[WebSocketEvent].flatMap { queue =>
      monad.flatten(monad.async[F[Response[T]]] { cb =>
        val initListener =
          new WebSocketInitListener(r, queue, (r: F[Response[T]]) => cb(Right(r)), t => cb(Left(t)))
        val h = new WebSocketUpgradeHandler.Builder()
          .addWebSocketListener(initListener)
          .build()

        val lf = ahcRequest.execute(h)

        Canceler(() => lf.abort(new InterruptedException))
      })
    }

  override def responseMonad: MonadError[F] = monad

  protected def bodyFromAHC: BodyFromAHC[F, S]
  protected def bodyToAHC: BodyToAHC[F, S]

  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]

  private def streamingAsyncHandler[T, R >: PE](
      request: Request[T, R],
      success: F[Response[T]] => Unit,
      error: Throwable => Unit
  ): AsyncHandler[Unit] = {
    new StreamedAsyncHandler[Unit] {
      private val builder = new AsyncResponse.ResponseBuilder()
      private var publisher: Option[Publisher[ByteBuffer]] = None
      private var completed = false
      // when using asStream(...), trying to detect ignored streams, where a subscription never happened
      @volatile private var subscribed = false

      override def onStream(p: Publisher[HttpResponseBodyPart]): AsyncHandler.State = {
        // Sadly we don't have .map on Publisher
        publisher = Some(new Publisher[ByteBuffer] {
          override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit = {
            subscribed = true
            p.subscribe(new Subscriber[HttpResponseBodyPart] {
              override def onError(t: Throwable): Unit = s.onError(t)
              override def onComplete(): Unit = s.onComplete()
              override def onNext(t: HttpResponseBodyPart): Unit =
                s.onNext(t.getBodyByteBuffer)
              override def onSubscribe(v: Subscription): Unit =
                s.onSubscribe(v)
            })
          }
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

          val baseResponse = readResponseNoBody(request, builder.build())
          val p = publisher.getOrElse(EmptyPublisher)
          val b = bodyFromAHC(Left(p), request.response, baseResponse, () => subscribed)

          success(b.map(t => baseResponse.copy(body = t)))
        }
      }

      override def onThrowable(t: Throwable): Unit = {
        error(t)
      }
    }
  }

  private class WebSocketInitListener[T](
      request: Request[T, _],
      queue: SimpleQueue[F, WebSocketEvent],
      success: F[Response[T]] => Unit,
      error: Throwable => Unit
  ) extends WebSocketListener {
    override def onOpen(ahcWebSocket: AHCWebSocket): Unit = {
      ahcWebSocket.removeWebSocketListener(this)
      val webSocket = WebSocketImpl.newCoupledToAHCWebSocket(ahcWebSocket, queue)
      queue.offer(WebSocketEvent.Open())
      val baseResponse =
        Response(
          (),
          StatusCode.SwitchingProtocols,
          "",
          readHeaders(ahcWebSocket.getUpgradeHeaders),
          Nil,
          request.onlyMetadata
        )
      val bf = bodyFromAHC(Right(webSocket), request.response, baseResponse, () => false)
      success(bf.map(b => baseResponse.copy(body = b)))
    }

    override def onClose(webSocket: AHCWebSocket, code: Int, reason: String): Unit = {
      throw new IllegalStateException("Should never be called, as the listener should be removed after onOpen")
    }

    override def onError(t: Throwable): Unit = error(t)
  }

  private def preparedRequest[R >: PE](r: Request[_, R]): F[BoundRequestBuilder] = {
    monad.fromTry(Try(asyncHttpClient.prepareRequest(requestToAsync(r)))).map(customizeRequest)
  }

  private def requestToAsync[R >: PE](r: Request[_, R]): AsyncRequest = {
    val readTimeout = r.options.readTimeout
    val rb = new RequestBuilder(r.method.method)
      .setUrl(r.uri.toString)
      .setReadTimeout(if (readTimeout.isFinite) readTimeout.toMillis.toInt else -1)
      .setRequestTimeout(if (readTimeout.isFinite) readTimeout.toMillis.toInt else -1)
    r.headers.foreach { header => rb.setHeader(header.name, header.value) }
    bodyToAHC(r, r.body, rb)
    rb.build()
  }

  private def readResponseNoBody(request: Request[_, _], response: AsyncResponse): Response[Unit] = {
    client3.Response(
      (),
      StatusCode.unsafeApply(response.getStatusCode),
      response.getStatusText,
      readHeaders(response.getHeaders),
      Nil,
      request.onlyMetadata
    )
  }

  private def readHeaders(h: HttpHeaders): Seq[Header] =
    h.iteratorAsString()
      .asScala
      .map(e => Header(e.getKey, e.getValue))
      .toList

  override def close(): F[Unit] = {
    if (closeClient) monad.eval(asyncHttpClient.close()) else monad.unit(())
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )
}

object AsyncHttpClientBackend {
  val DefaultWebSocketBufferCapacity: Option[Int] = Some(1024)

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
