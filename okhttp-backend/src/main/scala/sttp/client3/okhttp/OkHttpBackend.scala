package sttp.client3.okhttp

import java.io.{InputStream, UnsupportedEncodingException}
import java.util.concurrent.TimeUnit
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import okhttp3.internal.http.HttpMethod
import okhttp3.{
  Authenticator,
  Credentials,
  OkHttpClient,
  Route,
  Request => OkHttpRequest,
  RequestBody => OkHttpRequestBody,
  Response => OkHttpResponse
}
import sttp.capabilities.{Effect, Streams}
import sttp.client3.SttpBackendOptions.Proxy
import sttp.client3.SttpClientException.ReadException
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.okhttp.OkHttpBackend.EncodingHandler
import sttp.client3.{Response, SttpBackend, SttpBackendOptions, _}
import sttp.model._

import scala.collection.JavaConverters._

abstract class OkHttpBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends SttpBackend[F, P] {

  val streams: Streams[S]
  type PE = P with Effect[F]

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    adjustExceptions(request.isWebSocket, request) {
      if (request.isWebSocket) {
        sendWebSocket(request)
      } else {
        sendRegular(request)
      }
    }
  }

  protected def sendRegular[T, R >: PE](request: Request[T, R]): F[Response[T]]
  protected def sendWebSocket[T, R >: PE](request: Request[T, R]): F[Response[T]]

  private def adjustExceptions[T](isWebsocket: Boolean, request: Request[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      OkHttpBackend.exceptionToSttpClientException(isWebsocket, request, _)
    )

  private[okhttp] def convertRequest[T, R >: PE](request: Request[T, R]): OkHttpRequest = {
    val builder = new OkHttpRequest.Builder()
      .url(request.uri.toString)

    val body = bodyToOkHttp(request.body, request.headers.find(_.is(HeaderNames.ContentType)).map(_.value))
    builder.method(
      request.method.method,
      body.getOrElse {
        if (HttpMethod.requiresRequestBody(request.method.method))
          OkHttpRequestBody.create("", null)
        else null
      }
    )

    request.headers.foreach { header => builder.addHeader(header.name, header.value) }

    builder.build()
  }

  protected val bodyToOkHttp: BodyToOkHttp[F, S]
  protected val bodyFromOkHttp: BodyFromOkHttp[F, S]

  private[okhttp] def readResponse[T, R >: PE](
      res: OkHttpResponse,
      request: Request[T, R]
  ): F[Response[T]] = {
    val headers = readHeaders(res)
    val responseMetadata = ResponseMetadata(StatusCode(res.code()), res.message(), headers)
    val encoding = headers.collectFirst { case h if h.is(HeaderNames.ContentEncoding) => h.value }
    val method = Method(res.request().method())
    val byteBody = if (method != Method.HEAD) {
      encoding
        .map(e =>
          customEncodingHandler //There is no PartialFunction.fromFunction in scala 2.12
            .orElse(EncodingHandler(standardEncoding))(res.body().byteStream() -> e)
        )
        .getOrElse(res.body().byteStream())
    } else {
      res.body().byteStream()
    }

    val body = bodyFromOkHttp(byteBody, request.response, responseMetadata, None)
    responseMonad.map(body)(Response(_, StatusCode(res.code()), res.message(), headers, Nil, request.onlyMetadata))
  }

  private def readHeaders[R >: PE, T](res: OkHttpResponse) = {
    res
      .headers()
      .names()
      .asScala
      .flatMap(name => res.headers().values(name).asScala.map(Header(name, _)))
      .toList
  }

  private def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  override def close(): F[Unit] =
    if (closeClient) {
      responseMonad.eval(client.dispatcher().executorService().shutdown())
    } else responseMonad.unit(())

  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]

}

object OkHttpBackend {
  val DefaultWebSocketBufferCapacity: Option[Int] = Some(1024)
  type EncodingHandler = PartialFunction[(InputStream, String), InputStream]

  object EncodingHandler {
    def apply(f: (InputStream, String) => InputStream): EncodingHandler = { case (i, s) => f(i, s) }
  }

  private class ProxyAuthenticator(auth: SttpBackendOptions.ProxyAuth) extends Authenticator {
    override def authenticate(route: Route, response: OkHttpResponse): OkHttpRequest = {
      val credential = Credentials.basic(auth.username, auth.password)
      response.request.newBuilder.header("Proxy-Authorization", credential).build
    }
  }

  private[okhttp] def defaultClient(readTimeout: Long, options: SttpBackendOptions): OkHttpClient = {
    var clientBuilder = new OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(options.connectionTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeout, TimeUnit.MILLISECONDS)

    clientBuilder = options.proxy match {
      case None => clientBuilder
      case Some(p @ Proxy(_, _, _, _, Some(auth), _)) =>
        clientBuilder.proxySelector(p.asJavaProxySelector).proxyAuthenticator(new ProxyAuthenticator(auth))
      case Some(p) => clientBuilder.proxySelector(p.asJavaProxySelector)
    }

    clientBuilder.build()
  }

  private[okhttp] def updateClientIfCustomReadTimeout[T, S](r: Request[T, S], client: OkHttpClient): OkHttpClient = {
    val readTimeout = r.options.readTimeout
    if (readTimeout == DefaultReadTimeout) client
    else
      client
        .newBuilder()
        .readTimeout(if (readTimeout.isFinite) readTimeout.toMillis else 0, TimeUnit.MILLISECONDS)
        .build()
  }

  private[okhttp] def exceptionToSttpClientException(
      isWebsocket: Boolean,
      request: Request[_, _],
      e: Exception
  ): Option[Exception] =
    e match {
      // if the websocket protocol upgrade fails, OkHttp throws a ProtocolException - however the whole request has
      // been already sent, so this is not a TCP-level connect exception
      case e: java.net.ProtocolException if isWebsocket => Some(new ReadException(request, e))
      case e                                            => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }
}
