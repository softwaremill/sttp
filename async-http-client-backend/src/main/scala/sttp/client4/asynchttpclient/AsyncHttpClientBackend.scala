package sttp.client4.asynchttpclient

import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient.proxy.ProxyServer
import org.asynchttpclient.ws.{WebSocket => AHCWebSocket, WebSocketListener, WebSocketUpgradeHandler}
import org.asynchttpclient.{
  AsyncHttpClient,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig,
  Realm,
  Request => AHCRequest,
  RequestBuilder,
  Response => AHCResponse
}
import sttp.capabilities.Effect
import sttp.client4
import sttp.client4.BackendOptions.ProxyType.{Http, Socks}
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.client4.{BackendOptions, GenericBackend, Response, _}
import sttp.model._
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError}

import scala.collection.JavaConverters._
import scala.jdk.DurationConverters._
import scala.util.Try
import scala.collection.immutable.Seq

abstract class AsyncHttpClientBackend[F[_], P](
    asyncHttpClient: AsyncHttpClient,
    override implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends GenericBackend[F, P]
    with Backend[F] {

  type R = P with Effect[F]

  override def send[T](r: GenericRequest[T, R]): F[Response[T]] =
    adjustExceptions(r) {
      preparedRequest(r).flatMap { ahcRequest =>
        if (r.isWebSocket) sendWebSocket(r, ahcRequest) else sendRegular(r, ahcRequest)
      }
    }

  private def sendRegular[T](r: GenericRequest[T, R], ahcRequest: BoundRequestBuilder): F[Response[T]] =
    monad.flatten(monad.async[F[Response[T]]] { cb =>
      val lf = ahcRequest.execute()
      lf.addListener(
        new Runnable {
          override def run(): Unit =
            try {
              val ahcResponse = lf.get()
              cb(Right {
                val baseSttpResponse = readResponseNoBody(r, ahcResponse)
                bodyFromAHC(Left(ahcResponse), r.response, baseSttpResponse).map(t => baseSttpResponse.copy(body = t))
              })
            } catch {
              case t: Throwable => cb(Left(t))
            }
        },
        null
      )
      Canceler(() => lf.cancel(true))
    })

  private def sendWebSocket[T, R](r: GenericRequest[T, R], ahcRequest: BoundRequestBuilder): F[Response[T]] =
    createSimpleQueue[WebSocketEvent].flatMap { queue =>
      monad.flatten(monad.async[F[Response[T]]] { cb =>
        val initListener =
          new WebSocketInitListener(r, queue, (r: F[Response[T]]) => cb(Right(r)), t => cb(Left(t)))
        val h = new WebSocketUpgradeHandler.Builder()
          .addWebSocketListener(initListener)
          .build()

        val lf = ahcRequest.execute(h)

        Canceler(() => lf.cancel(true))
      })
    }

  private val bodyFromAHC: BodyFromAHC[F] = new BodyFromAHC[F]
  private val bodyToAHC: BodyToAHC[F] = new BodyToAHC[F]

  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]

  private class WebSocketInitListener[T](
      request: GenericRequest[T, _],
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
      val bf = bodyFromAHC(Right(webSocket), request.response, baseResponse)
      success(bf.map(b => baseResponse.copy(body = b)))
    }

    override def onClose(webSocket: AHCWebSocket, code: Int, reason: String): Unit =
      throw new IllegalStateException("Should never be called, as the listener should be removed after onOpen")

    override def onError(t: Throwable): Unit = error(t)
  }

  private def preparedRequest[R](r: GenericRequest[_, R]): F[BoundRequestBuilder] =
    monad.fromTry(Try(asyncHttpClient.prepareRequest(requestToAsync(r)))).map(customizeRequest)

  private def requestToAsync[R](r: GenericRequest[_, R]): AHCRequest = {
    val readTimeoutScalaDuration = r.options.readTimeout
    val readTimeout =
      if (readTimeoutScalaDuration.isFinite) java.time.Duration.ofMillis(readTimeoutScalaDuration.toMillis) else null
    val rb = new RequestBuilder(r.method.method)
      .setUrl(r.uri.toString)
      .setReadTimeout(readTimeout)
      .setRequestTimeout(readTimeout)
    r.headers.foreach(header => rb.setHeader(header.name, header.value))
    bodyToAHC(r, r.body, rb)
    rb.build()
  }

  private def readResponseNoBody(request: GenericRequest[_, _], response: AHCResponse): Response[Unit] =
    client4.Response(
      (),
      StatusCode.unsafeApply(response.getStatusCode),
      response.getStatusText,
      readHeaders(response.getHeaders),
      Nil,
      request.onlyMetadata
    )

  private def readHeaders(h: HttpHeaders): Seq[Header] =
    h.iteratorAsString()
      .asScala
      .map(e => Header(e.getKey, e.getValue))
      .toList

  override def close(): F[Unit] =
    if (closeClient) monad.eval(asyncHttpClient.close()) else monad.unit(())

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )
}

object AsyncHttpClientBackend {
  val DefaultWebSocketBufferCapacity: Option[Int] = Some(1024)

  private[asynchttpclient] def defaultConfigBuilder(
      options: BackendOptions
  ): DefaultAsyncHttpClientConfig.Builder = {
    val configBuilder = new DefaultAsyncHttpClientConfig.Builder()
      .setConnectTimeout(options.connectionTimeout.toJava)
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

  private[asynchttpclient] def defaultClient(options: BackendOptions): AsyncHttpClient =
    new DefaultAsyncHttpClient(defaultConfigBuilder(options).build())

  private[asynchttpclient] def clientWithModifiedOptions(
      options: BackendOptions,
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder
  ): AsyncHttpClient =
    new DefaultAsyncHttpClient(updateConfig(defaultConfigBuilder(options)).build())
}
