package sttp.client3.httpclient

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{Authenticator, PasswordAuthentication}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Executor, ThreadPoolExecutor}
import java.util.function

import sttp.capabilities.{Effect, Streams}
import sttp.client3.SttpBackendOptions.Proxy
import sttp.client3.httpclient.HttpClientBackend.EncodingHandler
import sttp.client3.{MultipartBody, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.ws.WebSocket

import scala.collection.JavaConverters._

abstract class HttpClientBackend[F[_], S, P, B](
    client: HttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler[B]
) extends SttpBackend[F, P] {
  val streams: Streams[S]
  type PE = P with Effect[F]

  protected def bodyToHttpClient: BodyToHttpClient[F, S]
  protected def bodyFromHttpClient: BodyFromHttpClient[F, S, B]

  private[httpclient] def convertRequest[T, R >: PE](request: Request[T, R]): F[HttpRequest] =
    monad.suspend {
      val builder = HttpRequest
        .newBuilder()
        .uri(request.uri.toJavaUri)

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
        builder.timeout(JDuration.ofMillis(request.options.readTimeout.toMillis)).build()
      }
    }

  private implicit val monad: MonadError[F] = responseMonad

  private[httpclient] def readResponse[T, R >: PE](
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
            override def apply(t: Executor): Unit = t.asInstanceOf[ThreadPoolExecutor].shutdown()
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

  private[httpclient] def defaultClient(options: SttpBackendOptions): HttpClient = {
    var clientBuilder = HttpClient
      .newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(JDuration.ofMillis(options.connectionTimeout.toMillis))

    clientBuilder = options.proxy match {
      case None => clientBuilder
      case Some(p @ Proxy(_, _, _, _, Some(auth), _)) =>
        clientBuilder.proxy(p.asJavaProxySelector).authenticator(new ProxyAuthenticator(auth))
      case Some(p) => clientBuilder.proxy(p.asJavaProxySelector)
    }

    clientBuilder.build()
  }
}
