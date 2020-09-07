package sttp.client.httpclient

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{Authenticator, PasswordAuthentication}
import java.nio.{Buffer, ByteBuffer}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Executor, ThreadPoolExecutor}
import java.util.function
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import sttp.client.ResponseAs.EagerResponseHandler
import sttp.client.SttpBackendOptions.Proxy
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.internal.FileHelpers
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
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
import sttp.model._

import scala.collection.JavaConverters._
import scala.language.higherKinds
import scala.util.{Failure, Try}

abstract class HttpClientBackend[F[_], S](
    client: HttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends SttpBackend[F, S, WebSocketHandler] {
  private[httpclient] def convertRequest[T](request: Request[T, S]): F[HttpRequest] =
    monad.suspend {
      val builder = HttpRequest
        .newBuilder()
        .uri(request.uri.toJavaUri)

      // Only setting the content type if it's present, and won't be set later with the multipart boundary added
      val contentType: Option[String] = request.headers.find(_.is(HeaderNames.ContentType)).map(_.value)
      contentType.foreach { ct =>
        request.body match {
          case _: MultipartBody => // skip, will be set later
          case _                => builder.header(HeaderNames.ContentType, ct)
        }
      }

      bodyToHttpBody(request, builder, contentType).map { httpBody =>
        builder.method(request.method.method, httpBody)
        request.headers
          .filterNot(h => (h.name == HeaderNames.ContentLength) || h.name == HeaderNames.ContentType)
          .foreach(h => builder.header(h.name, h.value))
        builder.timeout(JDuration.ofMillis(request.options.readTimeout.toMillis)).build()
      }
    }

  implicit val monad: MonadError[F] = responseMonad

  private def bodyToHttpBody[T](
      request: Request[T, S],
      builder: HttpRequest.Builder,
      contentType: Option[String]
  ): F[BodyPublisher] = {
    request.body match {
      case NoBody              => BodyPublishers.noBody().unit
      case StringBody(b, _, _) => BodyPublishers.ofString(b).unit
      case ByteArrayBody(b, _) => BodyPublishers.ofByteArray(b).unit
      case ByteBufferBody(b, _) =>
        if ((b: Buffer).isReadOnly()) BodyPublishers.ofInputStream(() => new ByteBufferBackedInputStream(b)).unit
        else BodyPublishers.ofByteArray(b.array()).unit
      case InputStreamBody(b, _) => BodyPublishers.ofInputStream(() => b).unit
      case FileBody(f, _)        => BodyPublishers.ofFile(f.toFile.toPath).unit
      case StreamBody(s)         => streamToRequestBody(s)
      case MultipartBody(parts) =>
        val multipartBodyPublisher = multipartBody(parts)
        val baseContentType = contentType.getOrElse("multipart/form-data")
        builder.header(HeaderNames.ContentType, s"$baseContentType; boundary=${multipartBodyPublisher.getBoundary}")
        multipartBodyPublisher.build().unit
    }
  }

  def streamToRequestBody(stream: S): F[BodyPublisher] =
    responseMonad.error(new IllegalStateException("Streaming isn't supported"))

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
      res: HttpResponse[InputStream],
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
    val responseMetadata = ResponseMetadata(headers, code, "")

    val encoding = headers.collectFirst { case h if h.is(HeaderNames.ContentEncoding) => h.value }
    val method = Method(res.request().method())
    val byteBody = if (method != Method.HEAD) {
      encoding
        .map(e => customEncodingHandler.orElse(PartialFunction.fromFunction(standardEncoding.tupled))(res.body() -> e))
        .getOrElse(res.body())
    } else {
      res.body()
    }
    val body = responseHandler(byteBody).handle(responseAs, responseMonad, responseMetadata)
    responseMonad.map(body)(Response(_, code, "", headers, Nil))
  }

  private def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  private def responseHandler(responseBody: InputStream) =
    new EagerResponseHandler[S] {
      override def handleBasic[T](bra: BasicResponseAs[T, S]): Try[T] =
        bra match {
          case IgnoreResponse =>
            Try(responseBody.close())
          case ResponseAsByteArray =>
            val result = Try(responseBody.readAllBytes())
            responseBody.close()
            result
          case ras @ ResponseAsStream() =>
            // TODO: dotty requires the cast below
            responseBodyToStream(responseBody).map(ras.responseIsStream.asInstanceOf[S =:= T])
          case ResponseAsFile(file) =>
            val body = Try(FileHelpers.saveFile(file.toFile, responseBody))
            responseBody.close()
            body.map(_ => file)
        }
    }

  // https://stackoverflow.com/a/6603018/362531
  private class ByteBufferBackedInputStream(buf: ByteBuffer) extends InputStream {
    override def read: Int = {
      if (!buf.hasRemaining) return -1
      buf.get & 0xff
    }

    override def read(bytes: Array[Byte], off: Int, len: Int): Int = {
      if (!buf.hasRemaining) return -1
      val len2 = Math.min(len, buf.remaining)
      buf.get(bytes, off, len2)
      len2
    }
  }

  def responseBodyToStream(responseBody: InputStream): Try[S] =
    Failure(new IllegalStateException("Streaming isn't supported"))

  override def close(): F[Unit] = {
    if (closeClient) {
      responseMonad.eval(
        client
          .executor()
          .map(new function.Function[Executor, Unit] {
            override def apply(t: Executor): Unit = t.asInstanceOf[ThreadPoolExecutor].shutdown()
          })
      )
    } else {
      responseMonad.unit(())
    }
  }
}

object HttpClientBackend {

  type EncodingHandler = PartialFunction[(InputStream, String), InputStream]
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
