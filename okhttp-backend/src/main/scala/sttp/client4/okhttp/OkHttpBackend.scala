package sttp.client4.okhttp

import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.internal.http.HttpMethod
import okhttp3.{
  Authenticator,
  Credentials,
  OkHttpClient,
  Request => OkHttpRequest,
  RequestBody => OkHttpRequestBody,
  Response => OkHttpResponse,
  Route
}
import sttp.capabilities.{Effect, Streams}
import sttp.client4.BackendOptions.Proxy
import sttp.client4.SttpClientException.ReadException
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4._
import sttp.model._

import scala.collection.JavaConverters._
import sttp.client4.compression.Compressor
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Decompressor
import sttp.client4.internal.FailingLimitedInputStream
import sttp.client4.internal.OnEndInputStream
import sttp.client4.GenericResponseAs.isWebSocket

abstract class OkHttpBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    closeClient: Boolean,
    compressionHandlers: CompressionHandlers[P, InputStream]
) extends GenericBackend[F, P]
    with Backend[F] {

  val streams: Streams[S]
  type R = P with Effect[F]

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] =
    adjustExceptions(request.isWebSocket, request) {
      if (request.isWebSocket) {
        sendWebSocket(request)
      } else {
        sendRegular(request)
      }
    }

  protected def sendRegular[T](request: GenericRequest[T, R]): F[Response[T]]
  protected def sendWebSocket[T](request: GenericRequest[T, R]): F[Response[T]]

  private def adjustExceptions[T](isWebsocket: Boolean, request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(
      OkHttpBackend.exceptionToSttpClientException(isWebsocket, request, _)
    )

  private[okhttp] def convertRequest[T](request: GenericRequest[T, R]): OkHttpRequest = {
    val builder = new OkHttpRequest.Builder()
      .url(request.uri.toString)

    val (maybeCompressedBody, contentLength) = Compressor.compressIfNeeded(request, compressionHandlers.compressors)
    val body = bodyToOkHttp(maybeCompressedBody, request.contentType, contentLength)
    builder.method(
      request.method.method,
      body.getOrElse {
        if (HttpMethod.requiresRequestBody(request.method.method))
          OkHttpRequestBody.create("", null)
        else null
      }
    )

    // the content-length header's value might have changed due to compression
    request.headers.foreach(header =>
      if (!header.is(HeaderNames.ContentLength)) {
        val _ = builder.addHeader(header.name, header.value)
      }
    )
    contentLength.foreach(cl => builder.addHeader(HeaderNames.ContentLength, cl.toString))

    builder.build()
  }

  protected val bodyToOkHttp: BodyToOkHttp[F, S]
  protected val bodyFromOkHttp: BodyFromOkHttp[F, S]

  private[okhttp] def readResponse[T](
      res: OkHttpResponse,
      request: GenericRequest[_, R],
      responseAs: ResponseAsDelegate[T, R],
      isWebSocket: Boolean
  ): F[Response[T]] = {
    val headers = readHeaders(res)
    val responseMetadata = ResponseMetadata(StatusCode(res.code()), res.message(), headers)
    val encoding = headers.collectFirst { case h if h.is(HeaderNames.ContentEncoding) => h.value }
    val method = Method(res.request().method())
    val inputStream = res.body().byteStream()
    val limitedInputStream =
      request.maxResponseBodyLength.fold(inputStream)(l => new FailingLimitedInputStream(inputStream, l))
    val decompressedInputStream =
      if (
        method != Method.HEAD && !res
          .code()
          .equals(StatusCode.NoContent.code) && request.autoDecompressionEnabled
      ) {
        encoding
          .filterNot(_.isEmpty)
          .map(e => Decompressor.decompressIfPossible(limitedInputStream, e, compressionHandlers.decompressors))
          .getOrElse(limitedInputStream)
      } else {
        limitedInputStream
      }

    val inputStreamWithCallback =
      if (isWebSocket) decompressedInputStream
      else
        new OnEndInputStream(decompressedInputStream, () => request.options.onBodyReceived(responseMetadata))

    val body = bodyFromOkHttp(inputStreamWithCallback, responseAs, responseMetadata, None)
    monad.map(body)(Response(_, StatusCode(res.code()), res.message(), headers, Nil, request.onlyMetadata))
  }

  private def readHeaders(res: OkHttpResponse): List[Header] =
    res
      .headers()
      .names()
      .asScala
      .flatMap(name => res.headers().values(name).asScala.map(Header(name, _)))
      .toList

  override def close(): F[Unit] =
    if (closeClient) {
      monad.eval(client.dispatcher().executorService().shutdown())
    } else monad.unit(())

  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]

}

object OkHttpBackend {
  val DefaultWebSocketBufferCapacity: Option[Int] = Some(1024)

  private class ProxyAuthenticator(auth: BackendOptions.ProxyAuth) extends Authenticator {
    override def authenticate(route: Route, response: OkHttpResponse): OkHttpRequest = {
      val credential = Credentials.basic(auth.username, auth.password)
      response.request.newBuilder.header("Proxy-Authorization", credential).build
    }
  }

  private[okhttp] def defaultClient(readTimeout: Long, options: BackendOptions): OkHttpClient = {
    var clientBuilder = new OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(options.connectionTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeout, TimeUnit.MILLISECONDS)

    clientBuilder = options.proxy match {
      case None                                       => clientBuilder
      case Some(p @ Proxy(_, _, _, _, Some(auth), _)) =>
        clientBuilder.proxySelector(p.asJavaProxySelector).proxyAuthenticator(new ProxyAuthenticator(auth))
      case Some(p) => clientBuilder.proxySelector(p.asJavaProxySelector)
    }

    clientBuilder.build()
  }

  private[okhttp] def updateClientIfCustomReadTimeout[T, S](
      r: GenericRequest[T, S],
      client: OkHttpClient
  ): OkHttpClient = {
    val readTimeoutMillis = if (r.options.readTimeout.isFinite) r.options.readTimeout.toMillis else 0
    val reuseClient = readTimeoutMillis == client.readTimeoutMillis()
    if (reuseClient) client
    else
      client
        .newBuilder()
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()
  }

  private[okhttp] def exceptionToSttpClientException(
      isWebsocket: Boolean,
      request: GenericRequest[_, _],
      e: Exception
  ): Option[Exception] =
    e match {
      // if the websocket protocol upgrade fails, OkHttp throws a ProtocolException - however the whole request has
      // been already sent, so this is not a TCP-level connect exception
      case e: java.net.ProtocolException if isWebsocket => Some(new ReadException(request, e))
      case e => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }
}
