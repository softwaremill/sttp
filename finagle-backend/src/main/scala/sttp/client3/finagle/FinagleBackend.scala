package sttp.client3.finagle

import com.twitter.finagle.Http.Client
import com.twitter.finagle.http.{
  FileElement,
  FormElement,
  RequestBuilder,
  SimpleElement,
  Method => FMethod,
  Response => FResponse
}
import com.twitter.finagle.{Http, Service, http}
import com.twitter.io.Buf
import com.twitter.io.Buf.{ByteArray, ByteBuffer}
import com.twitter.util
import com.twitter.util.{Duration, Future => TFuture}
import sttp.capabilities.Effect
import sttp.client3.internal.{BodyFromResponseAs, FileHelpers, SttpFile, Utf8}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client3.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  FollowRedirectsBackend,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  RequestBody,
  Response,
  StreamBody,
  StringBody,
  SttpBackend,
  SttpClientException,
  WebSocketResponseAs
}
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.io.Source

class FinagleBackend(client: Option[Client] = None) extends SttpBackend[TFuture, Any] {
  type PE = Any with Effect[TFuture]
  override def send[T, R >: PE](request: Request[T, R]): TFuture[Response[T]] =
    adjustExceptions(request) {
      val service = getClient(client, request)
      val finagleRequest = requestBodyToFinagle(request)
      service
        .apply(finagleRequest)
        .flatMap { fResponse =>
          val code = StatusCode.unsafeApply(fResponse.statusCode)
          val headers = fResponse.headerMap.map(h => Header(h._1, h._2)).toList
          val statusText = fResponse.status.reason
          val responseMetadata = ResponseMetadata(code, statusText, headers)
          val body = bodyFromResponseAs(request.response, responseMetadata, Left(fResponse))
          service
            .close()
            .flatMap(_ => body.map(sttp.client3.Response(_, code, statusText, headers, Nil, request.onlyMetadata)))
        }
        .rescue { case e: Exception =>
          service.close().flatMap(_ => TFuture.exception(e))
        }
    }

  override def close(): TFuture[Unit] = TFuture.Done

  override implicit val responseMonad: MonadError[TFuture] = TFutureMonadError

  private def headersToMap(headers: Seq[Header]): Map[String, String] = {
    headers.map(header => header.name -> header.value).toMap
  }

  private def methodToFinagle(m: Method): FMethod =
    m match {
      case Method.GET     => FMethod.Get
      case Method.HEAD    => FMethod.Head
      case Method.POST    => FMethod.Post
      case Method.PUT     => FMethod.Put
      case Method.DELETE  => FMethod.Delete
      case Method.OPTIONS => FMethod.Options
      case Method.PATCH   => FMethod.Patch
      case Method.CONNECT => FMethod.Connect
      case Method.TRACE   => FMethod.Trace
      case _              => FMethod(m.method)
    }

  private def requestBodyToFinagle(r: Request[_, Nothing]): http.Request = {
    val finagleMethod = methodToFinagle(r.method)
    val url = r.uri.toString
    val headers = headersToMap(r.headers)

    r.body match {
      case FileBody(f, _) =>
        val content: String = Source.fromFile(f.toFile).mkString
        buildRequest(url, headers, finagleMethod, Some(ByteArray(content.getBytes: _*)))
      case NoBody               => buildRequest(url, headers, finagleMethod, None)
      case StringBody(s, e, _)  => buildRequest(url, headers, finagleMethod, Some(ByteArray(s.getBytes(e): _*)))
      case ByteArrayBody(b, _)  => buildRequest(url, headers, finagleMethod, Some(ByteArray(b: _*)))
      case ByteBufferBody(b, _) => buildRequest(url, headers, finagleMethod, Some(ByteBuffer.Owned(b)))
      case InputStreamBody(is, _) =>
        buildRequest(
          url,
          headers,
          finagleMethod,
          Some(ByteArray(Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray: _*))
        )
      case MultipartBody(parts) =>
        val requestBuilder = RequestBuilder.create().url(url).addHeaders(headers)
        val elements = parts.map { part => getBasicBodyContent(part) }
        requestBuilder.add(elements).buildFormPost(true)
      //requestBuilder.addFormElement(elements: _*).buildFormPost(true)
      case _ => buildRequest(url, headers, finagleMethod, None)
    }
  }

  private def getBasicBodyContent(part: Part[RequestBody[_]]): FormElement = {

    val content: String = part.body match {
      case StringBody(s, e, _) if e.equalsIgnoreCase(Utf8) => s
      case StringBody(s, e, _)                             => Source.fromBytes(s.getBytes(e)).mkString
      case ByteArrayBody(b, _)                             => Source.fromBytes(b).mkString
      case ByteBufferBody(b, _)                            => Source.fromBytes(b.array()).mkString
      case InputStreamBody(is, _)                          => Source.fromInputStream(is).mkString
      case FileBody(f, _)                                  => Source.fromFile(f.toFile).mkString
      case NoBody                                          => ""
      case StreamBody(_)                                   => throw new IllegalArgumentException("Streaming is not supported")
      case MultipartBody(_)                                => throw new IllegalArgumentException("Nested multipart bodies are not supported")
    }

    part.fileName match {
      case Some(_) => FileElement(part.name, ByteArray(content.getBytes: _*), part.contentType, part.fileName)
      case None if part.contentType.exists(_.equalsIgnoreCase(MediaType.TextPlainUtf8.toString())) =>
        SimpleElement(part.name, content)
      case None => FileElement(part.name, ByteArray(content.getBytes: _*), part.contentType)
    }
  }

  private def buildRequest(
      url: String,
      headers: Map[String, String],
      method: FMethod,
      content: Option[Buf]
  ): http.Request = {
    val defaultHostHeader = RequestBuilder.create().url(url)
    // RequestBuilder#url() will set the `Host` Header to the url's hostname. That is not necessarily correct,
    // when the headers parameter overrides that setting, clear the default.
    val updatedHostHeader =
      if (headers.contains(HeaderNames.Host))
        defaultHostHeader.setHeader(HeaderNames.Host, Seq.empty)
      else
        defaultHostHeader

    updatedHostHeader.addHeaders(headers).build(method, content)
  }

  private lazy val bodyFromResponseAs =
    new BodyFromResponseAs[TFuture, FResponse, Nothing, Nothing] {
      override protected def withReplayableBody(
          response: FResponse,
          replayableBody: Either[Array[Byte], SttpFile]
      ): TFuture[FResponse] = {
        response.content(replayableBody match {
          case Left(byteArray) => Buf.ByteArray(byteArray: _*)
          case Right(file)     => Buf.ByteArray(FileHelpers.readFile(file.toFile): _*)
        })
      }.unit

      override protected def regularIgnore(response: FResponse): TFuture[Unit] = TFuture(response.clearContent())

      override protected def regularAsByteArray(response: FResponse): TFuture[Array[Byte]] =
        TFuture.const(util.Try {
          val bb = ByteBuffer.Owned.extract(response.content)
          val b = new Array[Byte](bb.remaining)
          bb.get(b)
          b
        })

      override protected def regularAsFile(response: FResponse, file: SttpFile): TFuture[SttpFile] = {
        TFuture.const(util.Try(FileHelpers.saveFile(file.toFile, response.getInputStream()))).map(_ => file)
      }

      override protected def regularAsStream(response: FResponse): TFuture[Nothing] =
        TFuture.exception(new IllegalStateException("Streaming isn't supported"))

      override protected def handleWS[T](
          responseAs: WebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: Nothing
      ): TFuture[T] = ws

      override protected def cleanupWhenNotAWebSocket(response: FResponse, e: NotAWebSocketException): TFuture[Unit] =
        TFuture.Done

      override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): TFuture[Unit] =
        response
    }

  private def getClient(c: Option[Client], request: Request[_, Nothing]): Service[http.Request, FResponse] = {
    val client = c.getOrElse {
      request.uri.scheme match {
        case Some("https") => Http.client.withTransport.tls
        case _             => Http.client
      }
    }
    client
      .withRequestTimeout(Duration.fromMilliseconds(request.options.readTimeout.toMillis))
      .newService(uriToFinagleDestination(request.uri))
  }

  private def uriToFinagleDestination(uri: Uri): String = {
    val defaultPort = uri.scheme match {
      case Some("https") => 443
      case _             => 80
    }
    s"${uri.host.getOrElse("localhost")}:${uri.port.getOrElse(defaultPort)}"
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => TFuture[T]): TFuture[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(exceptionToSttpClientException(request, _))

  private def exceptionToSttpClientException(request: Request[_, _], e: Exception): Option[Exception] =
    e match {
      case e: com.twitter.finagle.NoBrokersAvailableException =>
        Some(new SttpClientException.ConnectException(request, e))
      case e: com.twitter.finagle.Failure if e.getCause.isInstanceOf[com.twitter.finagle.ConnectionFailedException] =>
        Some(new SttpClientException.ConnectException(request, e))
      case e: com.twitter.finagle.ChannelClosedException => Some(new SttpClientException.ReadException(request, e))
      case e: com.twitter.finagle.IndividualRequestTimeoutException =>
        Some(new SttpClientException.ReadException(request, e))
      case e: Exception => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }
}

object TFutureMonadError extends MonadError[TFuture] {
  override def unit[T](t: T): TFuture[T] = TFuture.apply(t)
  override def map[T, T2](fa: TFuture[T])(f: T => T2): TFuture[T2] = fa.map(f)
  override def flatMap[T, T2](fa: TFuture[T])(f: T => TFuture[T2]): TFuture[T2] = fa.flatMap(f)
  override def error[T](t: Throwable): TFuture[T] = TFuture.exception(t)
  override protected def handleWrappedError[T](rt: TFuture[T])(
      h: PartialFunction[Throwable, TFuture[T]]
  ): TFuture[T] = rt.rescue(h)
  override def eval[T](t: => T): TFuture[T] = TFuture(t)
  override def ensure[T](f: TFuture[T], e: => TFuture[Unit]): TFuture[T] = f.ensure(e.toJavaFuture.get())
}

object FinagleBackend {

  def apply(): SttpBackend[TFuture, Any] = {
    new FollowRedirectsBackend[TFuture, Any](new FinagleBackend())
  }

  def usingClient(client: Client): SttpBackend[TFuture, Any] = {
    new FollowRedirectsBackend[TFuture, Any](new FinagleBackend(Some(client)))
  }

  /** Create a stub backend for testing, which uses the [[TFuture]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[TFuture, Any] = SttpBackendStub(TFutureMonadError)
}
