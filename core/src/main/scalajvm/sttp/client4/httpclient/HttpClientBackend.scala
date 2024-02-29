package sttp.client4.httpclient

import sttp.capabilities.{Effect, Streams}
import sttp.client4.BackendOptions.Proxy
import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.internal.SttpToJavaConverters.toJavaFunction
import sttp.client4.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.{
  Backend,
  BackendOptions,
  GenericBackend,
  GenericRequest,
  MultipartBody,
  Response,
  SttpClientException
}
import sttp.model.HttpVersion.{HTTP_1_1, HTTP_2}
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocket

import java.net.Authenticator.RequestorType
import java.net.http.{HttpClient, HttpRequest, HttpResponse, WebSocket => JWebSocket}
import java.net.{Authenticator, PasswordAuthentication}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Executor, ThreadPoolExecutor}
import java.util.function
import scala.collection.JavaConverters._

/** @param closeClient
  *   If the executor underlying the client is a [[ThreadPoolExecutor]], should it be shutdown on [[close]].
  */
abstract class HttpClientBackend[F[_], S <: Streams[S], P, B](
    client: HttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler[B]
) extends GenericBackend[F, P]
    with Backend[F] {
  val streams: Streams[S]

  type R = P with Effect[F]

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] =
    adjustExceptions(request) {
      if (request.isWebSocket) sendWebSocket(request) else sendRegular(request)
    }

  protected def sendRegular[T](request: GenericRequest[T, R]): F[Response[T]]

  protected def sendWebSocket[T](request: GenericRequest[T, R]): F[Response[T]]

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(monad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  protected def bodyToHttpClient: BodyToHttpClient[F, S]
  protected def bodyFromHttpClient: BodyFromHttpClient[F, S, B]

  private[client4] def convertRequest[T](request: GenericRequest[T, R]): F[HttpRequest] =
    monad.suspend {
      val builder = HttpRequest
        .newBuilder()
        .uri(request.uri.toJavaUri)

      request.httpVersion.foreach {
        case HTTP_1_1 => builder.version(HttpClient.Version.HTTP_1_1)
        case HTTP_2   => builder.version(HttpClient.Version.HTTP_2)
        case _        => // skip, client default version remains active
      }

      // Only setting the content type if it's present, and won't be set later with the mulitpart boundary added
      val contentType: Option[String] = request.headers.find(_.is(HeaderNames.ContentType)).map(_.value)
      contentType.foreach { ct =>
        request.body match {
          case _: MultipartBody[_] => // skip, will be set later
          case _                   => builder.header(HeaderNames.ContentType, ct)
        }
      }

      bodyToHttpClient(request, builder, contentType).map { httpBody =>
        builder.method(request.method.method, httpBody)
        request.headers
          .filterNot(h => (h.name == HeaderNames.ContentLength) || h.name == HeaderNames.ContentType)
          .foreach(h => builder.header(h.name, h.value))
        val timeout = request.options.readTimeout
        if (timeout.isFinite) {
          builder.timeout(JDuration.ofMillis(timeout.toMillis)).build()
        } else {
          //  The effect of not setting a timeout is the same as setting an infinite Duration,
          //  i.e. block forever.
          builder.build()
        }
      }
    }

  private implicit val _monad: MonadError[F] = monad

  private[client4] def readResponse[T](
      res: HttpResponse[_],
      resBody: Either[B, WebSocket[F]],
      request: GenericRequest[T, R]
  ): F[Response[T]] = {
    val headersMap = res.headers().map().asScala
    val headers = headersMap.keySet
      .flatMap(name => headersMap(name).asScala.map(Header(name, _)))
      .toList

    val code = StatusCode(res.statusCode())
    val responseMetadata = ResponseMetadata(code, "", headers)

    val encoding = headers.collectFirst { case h if h.is(HeaderNames.ContentEncoding) => h.value }
    val method = Method(res.request().method())
    val decodedResBody = if (method != Method.HEAD) {
      resBody.left
        .map { is =>
          encoding
            .filterNot(e => code.equals(StatusCode.NoContent) || request.autoDecompressionDisabled || e.isEmpty)
            .map(e => customEncodingHandler.applyOrElse((is, e), standardEncoding.tupled))
            .getOrElse(is)
        }
    } else {
      resBody
    }
    val body = bodyFromHttpClient(decodedResBody, request.response, responseMetadata)
    monad.map(body)(Response(_, code, "", headers, Nil, request.onlyMetadata))
  }

  protected def standardEncoding: (B, String) => B

  protected def prepareWebSocketBuilder[T](
      request: GenericRequest[T, R],
      client: HttpClient
  ): JWebSocket.Builder = {
    val wsSubProtocols = request.headers
      .find(_.is(HeaderNames.SecWebSocketProtocol))
      .map(_.value)
      .toSeq
      .flatMap(_.split(","))
      .map(_.trim)
      .toList
    val wsBuilder = wsSubProtocols match {
      case Nil          => client.newWebSocketBuilder()
      case head :: Nil  => client.newWebSocketBuilder().subprotocols(head)
      case head :: tail => client.newWebSocketBuilder().subprotocols(head, tail: _*)
    }
    client
      .connectTimeout()
      .map[java.net.http.WebSocket.Builder](toJavaFunction((d: JDuration) => wsBuilder.connectTimeout(d)))
    filterIllegalWsHeaders(request).headers.foreach(h => wsBuilder.header(h.name, h.value))
    wsBuilder
  }

  private def filterIllegalWsHeaders[T](request: GenericRequest[T, R]): GenericRequest[T, R] =
    request.withHeaders(request.headers.filter(h => !wsIllegalHeaders.contains(h.name.toLowerCase)))

  // these headers can't be sent using HttpClient; the SecWebSocketProtocol is supported through a builder method,
  // the resit is ignored
  private lazy val wsIllegalHeaders: Set[String] = {
    import HeaderNames._
    Set(SecWebSocketAccept, SecWebSocketExtensions, SecWebSocketKey, SecWebSocketVersion, SecWebSocketProtocol).map(
      _.toLowerCase
    )
  }
  override def close(): F[Unit] =
    if (closeClient) {
      monad.eval(
        client
          .executor()
          .map[Unit](new function.Function[Executor, Unit] {
            override def apply(t: Executor): Unit = t match {
              case tpe: ThreadPoolExecutor => tpe.shutdown()
              case _                       => ()
            }
          })
      )
    } else {
      monad.unit(())
    }
}

object HttpClientBackend {

  type EncodingHandler[B] = PartialFunction[(B, String), B]

  private class ProxyAuthenticator(auth: BackendOptions.ProxyAuth) extends Authenticator {
    override def getPasswordAuthentication: PasswordAuthentication =
      if (getRequestorType == RequestorType.PROXY) {
        new PasswordAuthentication(auth.username, auth.password.toCharArray)
      } else null
  }

  private[client4] def defaultClient(options: BackendOptions, executor: Option[Executor]): HttpClient = {
    var clientBuilder = HttpClient
      .newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(JDuration.ofMillis(options.connectionTimeout.toMillis))

    clientBuilder = executor.fold(clientBuilder)(clientBuilder.executor)

    clientBuilder = options.proxy match {
      case None => clientBuilder
      case Some(p @ Proxy(_, _, _, _, Some(auth), _)) =>
        clientBuilder.proxy(p.asJavaProxySelector).authenticator(new ProxyAuthenticator(auth))
      case Some(p) => clientBuilder.proxy(p.asJavaProxySelector)
    }

    clientBuilder.build()
  }
}
