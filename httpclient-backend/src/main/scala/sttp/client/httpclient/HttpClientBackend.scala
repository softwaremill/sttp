package sttp.client.httpclient

import java.io.InputStream
import java.net.http.HttpRequest.{BodyPublisher, BodyPublishers}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.{Authenticator, PasswordAuthentication}
import java.nio.{ByteBuffer, Buffer}
import java.time.{Duration => JDuration}
import java.util.concurrent.{Executor, ThreadPoolExecutor}
import java.util.function
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import sttp.client.ResponseAs.EagerResponseHandler
import sttp.client.SttpBackendOptions.Proxy
import sttp.client.internal.FileHelpers
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

abstract class HttpClientBackend[F[_], S](client: HttpClient, closeClient: Boolean)
    extends SttpBackend[F, S, WebSocketHandler] {
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
      case NoBody              => BodyPublishers.noBody()
      case StringBody(b, _, _) => BodyPublishers.ofString(b)
      case ByteArrayBody(b, _) => BodyPublishers.ofByteArray(b)
      case ByteBufferBody(b, _) =>
        if ((b: Buffer).isReadOnly()) BodyPublishers.ofInputStream(() => new ByteBufferBackedInputStream(b))
        else BodyPublishers.ofByteArray(b.array())
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
    val byteBody = if (encoding.contains("gzip") && method != Method.HEAD) {
      new GZIPInputStream(res.body())
    } else if (encoding.contains("deflate") && method != Method.HEAD) {
      new InflaterInputStream(res.body())
    } else {
      res.body()
    }
    val body = responseHandler(byteBody).handle(responseAs, responseMonad, responseMetadata)
    responseMonad.map(body)(Response(_, code, "", headers, Nil))
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
      buf.get & 0xFF
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
