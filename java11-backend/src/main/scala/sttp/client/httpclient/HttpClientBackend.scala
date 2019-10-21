package sttp.client.httpclient

import java.io.ByteArrayInputStream
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{Authenticator, PasswordAuthentication}
import java.time.{Duration => JDuration}

import sttp.client.ResponseAs.EagerResponseHandler
import sttp.client.SttpBackendOptions.Proxy
import sttp.client.internal.FileHelpers
import sttp.client.ws.WebSocketResponse
import sttp.client.{
  BasicRequestBody,
  BasicResponseAs,
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  IgnoreResponse,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsStream,
  ResponseMetadata,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpBackendOptions
}
import sttp.model.{Header, HeaderNames, Part, StatusCode}

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.util.{Failure, Try}

abstract class HttpClientBackend[F[_], S](client: HttpClient) extends SttpBackend[F, S, WebSocketResponse] {

  private[httpclient] def convertRequest[T](request: Request[T, S]) = {
    val builder = HttpRequest
      .newBuilder()
      .uri(request.uri.toJavaUri)

    builder.method(request.method.method, bodyToHttpBody(request, builder))
    request.headers
      .filterNot(_.name == HeaderNames.ContentLength)
      .foreach(h => builder.header(h.name, h.value))
    builder.timeout(JDuration.ofMillis(request.options.readTimeout.toMillis)).build()
  }

  private def bodyToHttpBody[T](request: Request[T, S], builder: HttpRequest.Builder) = {
    request.body match {
      case NoBody                => BodyPublishers.noBody()
      case StringBody(b, _, _)   => BodyPublishers.ofString(b)
      case ByteArrayBody(b, _)   => BodyPublishers.ofByteArray(b)
      case ByteBufferBody(b, _)  => BodyPublishers.ofByteArray(b.array())
      case InputStreamBody(b, _) => BodyPublishers.ofInputStream(() => b)
      case FileBody(f, _)        => BodyPublishers.ofFile(f.toFile.toPath)
      case StreamBody(s)         => streamToRequestBody(s)
      case MultipartBody(parts) =>
        val multipartBodyPublisher = multipartBody(parts)
        builder.header(HeaderNames.ContentType, s"multipart/form-data; boundary=${multipartBodyPublisher.getBoundary}")
        multipartBodyPublisher.build()
    }
  }

  def streamToRequestBody(stream: S): BodyPublisher = throw new IllegalStateException("Streaming isn't supported")

  private def multipartBody[T](parts: Seq[Part[BasicRequestBody]]) = {
    val multipartBuilder = new MultiPartBodyPublisher()
    parts.foreach { p =>
      val allHeaders = p.headers :+ Header(HeaderNames.ContentDisposition, p.contentDispositionHeaderValue)
      p.body match {
        case FileBody(f, _) =>
          multipartBuilder.addPart(p.name, f.toFile.toPath, allHeaders.map(h => h.name -> h.value).toMap.asJava)
        case StringBody(b, _, _) =>
          multipartBuilder.addPart(p.name, b, allHeaders.map(h => h.name -> h.value).toMap.asJava)
      }
    }
    multipartBuilder
  }

  private[httpclient] def readResponse[T](
      res: HttpResponse[Array[Byte]],
      responseAs: ResponseAs[T, S]
  ): F[Response[T]] = {

    val headers = res
      .headers()
      .map()
      .keySet()
      .asScala
      .flatMap(name => res.headers().map().asScala(name).asScala.map(Header(name, _)))
      .toList

    val code = StatusCode(res.statusCode())
    val message = "???" // TODO
    val responseMetadata = ResponseMetadata(headers, code, message)
    val body = responseHandler(res).handle(responseAs, responseMonad, responseMetadata)

    responseMonad.map(body)(Response(_, code, message, headers, Nil))
  }

  private def responseHandler(res: HttpResponse[Array[Byte]]) =
    new EagerResponseHandler[S] {
      override def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T] =
        bra match {
          case IgnoreResponse =>
            Try(())
          case ResponseAsByteArray =>
            val body = Try(res.body())
            body
          case ras @ ResponseAsStream() =>
            responseBodyToStream(res).map(ras.responseIsStream)
          case ResponseAsFile(file) =>
            val body = Try(FileHelpers.saveFile(file.toFile, new ByteArrayInputStream(res.body())))
            body.map(_ => file)
        }
    }

  def responseBodyToStream(res: HttpResponse[Array[Byte]]): Try[S] =
    Failure(new IllegalStateException("Streaming isn't supported"))

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WebSocketResponse[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = ???

  override def close(): F[Unit] = responseMonad.unit(())

}

object HttpBackend {

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
      case Some(p @ Proxy(_, _, _, _, Some(auth))) =>
        clientBuilder.proxy(p.asJavaProxySelector).authenticator(new ProxyAuthenticator(auth))
      case Some(p) => clientBuilder.proxy(p.asJavaProxySelector)
    }

    clientBuilder.build()
  }
}
