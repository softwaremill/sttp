package sttp.client3

import sttp.capabilities.{Effect, Streams}
import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.SttpBackendOptions.Proxy
import sttp.client3.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient}
import sttp.model.HttpVersion.{HTTP_1_1, HTTP_2}
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocket

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{Authenticator, PasswordAuthentication}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Executor, ThreadPoolExecutor}
import java.util.function
import scala.collection.JavaConverters._

/** @param closeClient
  *   If the executor underlying the client is a [[ThreadPoolExecutor]], should it be shutdown on [[close]].
  */
abstract class HttpClientBackend[F[_], S, P, B](
    client: HttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler[B]
) extends SttpBackend[F, P] {
  val streams: Streams[S]
  type PE = P with Effect[F]

  protected def bodyToHttpClient: BodyToHttpClient[F, S]
  protected def bodyFromHttpClient: BodyFromHttpClient[F, S, B]

  private[client3] def convertRequest[T, R >: PE](request: Request[T, R]): F[HttpRequest] =
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

  private implicit val monad: MonadError[F] = responseMonad

  private[client3] def readResponse[T, R >: PE](
      res: HttpResponse[_],
      resBody: Either[B, WebSocket[F]],
      request: Request[T, R]
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
    responseMonad.map(body)(Response(_, code, "", headers, Nil, request.onlyMetadata))
  }

  protected def standardEncoding: (B, String) => B

  override def close(): F[Unit] = {
    if (closeClient) {
      responseMonad.eval(
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
      responseMonad.unit(())
    }
  }
}

object HttpClientBackend {

  type EncodingHandler[B] = PartialFunction[(B, String), B]
  // TODO not sure if it works
  private class ProxyAuthenticator(auth: SttpBackendOptions.ProxyAuth) extends Authenticator {
    override def getPasswordAuthentication: PasswordAuthentication = {
      new PasswordAuthentication(auth.username, auth.password.toCharArray)
    }
  }

  // Left here for bincompat
  private[client3] def defaultClient(options: SttpBackendOptions): HttpClient =
    defaultClient(options, None)

  private[client3] def defaultClient(options: SttpBackendOptions, executor: Option[Executor]): HttpClient = {
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
