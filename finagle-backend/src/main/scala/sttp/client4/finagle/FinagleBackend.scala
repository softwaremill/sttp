package sttp.client4.finagle

import com.twitter.finagle.Http.Client
import com.twitter.finagle.http.{
  FileElement,
  FormElement,
  Method => FMethod,
  RequestBuilder,
  Response => FResponse,
  SimpleElement
}
import com.twitter.finagle.{http, Http, Service}
import com.twitter.io.Buf
import com.twitter.io.Buf.{ByteArray, ByteBuffer}
import com.twitter.util
import com.twitter.util.{Duration, Future => TFuture}
import sttp.capabilities.Effect
import sttp.client4.internal.{BodyFromResponseAs, FileHelpers, SttpFile, Utf8}
import sttp.client4.testing.BackendStub
import sttp.client4.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.client4.{wrappers, _}
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.HttpVersion.HTTP_1
import sttp.model._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.client4.compression.Compressor
import sttp.client4.compression.DeflateDefaultCompressor
import sttp.client4.compression.GZipDefaultCompressor

import scala.io.Source
import sttp.client4.internal.OnEndInputStream
import java.io.InputStream

class FinagleBackend(client: Option[Client] = None) extends Backend[TFuture] {
  type R = Any with Effect[TFuture]

  private val compressors: List[Compressor[R]] = List(new GZipDefaultCompressor(), new DeflateDefaultCompressor)

  override def send[T](request: GenericRequest[T, R]): TFuture[Response[T]] =
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
          val body =
            bodyFromResponseAs(() => request.options.onBodyReceived(responseMetadata))(
              request.response,
              responseMetadata,
              Left(fResponse)
            )
          service
            .close()
            .flatMap(_ => body.map(sttp.client4.Response(_, code, statusText, headers, Nil, request.onlyMetadata)))
        }
        .rescue { case e: Exception =>
          service.close().flatMap(_ => TFuture.exception(e))
        }
    }

  override def close(): TFuture[Unit] = TFuture.Done

  override implicit val monad: MonadError[TFuture] = TFutureMonadError

  private def headersToMap(headers: Seq[Header]): Map[String, String] =
    headers.map(header => header.name -> header.value).toMap

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

  private def requestBodyToFinagle(r: GenericRequest[_, R]): http.Request = {
    val finagleMethod = methodToFinagle(r.method)
    val url = r.uri.toString
    val (body, contentLength) = Compressor.compressIfNeeded(r, compressors)
    val headers = {
      val hh = headersToMap(r.headers) - HeaderNames.ContentLength
      contentLength.fold(hh)(cl => hh.updated(HeaderNames.ContentLength, cl.toString))
    }

    body match {
      case FileBody(f, _) =>
        val content: String = Source.fromFile(f.toFile).mkString
        buildRequest(url, headers, finagleMethod, Some(ByteArray(content.getBytes: _*)), r.httpVersion)
      case NoBody => buildRequest(url, headers, finagleMethod, None, r.httpVersion)
      case StringBody(s, e, _) =>
        buildRequest(url, headers, finagleMethod, Some(ByteArray(s.getBytes(e): _*)), r.httpVersion)
      case ByteArrayBody(b, _) => buildRequest(url, headers, finagleMethod, Some(ByteArray(b: _*)), r.httpVersion)
      case ByteBufferBody(b, _) =>
        buildRequest(url, headers, finagleMethod, Some(ByteBuffer.Owned(b)), r.httpVersion)
      case InputStreamBody(is, _) =>
        buildRequest(
          url,
          headers,
          finagleMethod,
          Some(ByteArray(Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray: _*)),
          r.httpVersion
        )
      case m: MultipartBody[_] =>
        val requestBuilder = RequestBuilder.create().url(url).addHeaders(headers)
        val elements = m.parts.map(part => getBasicBodyContent(part))
        requestBuilder.add(elements).buildFormPost(true)
      // requestBuilder.addFormElement(elements: _*).buildFormPost(true)
      case _ => buildRequest(url, headers, finagleMethod, None, r.httpVersion)
    }
  }

  private def getBasicBodyContent(part: Part[BodyPart[_]]): FormElement = {

    val content: String = part.body match {
      case StringBody(s, e, _) if e.equalsIgnoreCase(Utf8) => s
      case StringBody(s, e, _)                             => Source.fromBytes(s.getBytes(e)).mkString
      case ByteArrayBody(b, _)                             => Source.fromBytes(b).mkString
      case ByteBufferBody(b, _)                            => Source.fromBytes(b.array()).mkString
      case InputStreamBody(is, _)                          => Source.fromInputStream(is).mkString
      case FileBody(f, _)                                  => Source.fromFile(f.toFile).mkString
      case StreamBody(_) => throw new IllegalArgumentException("Streaming is not supported")
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
      content: Option[Buf],
      httpVersion: Option[HttpVersion]
  ): http.Request = {
    val defaultHostHeader = RequestBuilder.create().url(url)
    if (httpVersion.exists(_.equals(HTTP_1))) {
      defaultHostHeader.http10()
    }
    // RequestBuilder#url() will set the `Host` Header to the url's hostname. That is not necessarily correct,
    // when the headers parameter overrides that setting, clear the default.
    val updatedHostHeader =
      if (headers.contains(HeaderNames.Host))
        defaultHostHeader.setHeader(HeaderNames.Host, Seq.empty)
      else
        defaultHostHeader

    updatedHostHeader.addHeaders(headers).build(method, content)
  }

  private def bodyFromResponseAs(onBodyReceived: () => Unit) =
    new BodyFromResponseAs[TFuture, FResponse, Nothing, Nothing] {
      override protected def withReplayableBody(
          response: FResponse,
          replayableBody: Either[Array[Byte], SttpFile]
      ): TFuture[FResponse] =
        response
          .content(replayableBody match {
            case Left(byteArray) => Buf.ByteArray(byteArray: _*)
            case Right(file)     => Buf.ByteArray(FileHelpers.readFile(file.toFile): _*)
          })
          .unit

      override protected def regularIgnore(response: FResponse): TFuture[Unit] = TFuture {
        response.clearContent()
        onBodyReceived()
      }

      override protected def regularAsByteArray(response: FResponse): TFuture[Array[Byte]] =
        TFuture.const(util.Try {
          val bb = ByteBuffer.Owned.extract(response.content)
          val b = new Array[Byte](bb.remaining)
          bb.get(b)
          onBodyReceived()
          b
        })

      override protected def regularAsFile(response: FResponse, file: SttpFile): TFuture[SttpFile] =
        TFuture
          .const(
            util.Try(FileHelpers.saveFile(file.toFile, new OnEndInputStream(response.getInputStream(), onBodyReceived)))
          )
          .map(_ => file)

      override protected def regularAsInputStream(response: FResponse): TFuture[InputStream] = TFuture.const(
        util.Try(new OnEndInputStream(response.getInputStream(), onBodyReceived))
      )

      override protected def regularAsStream(response: FResponse): TFuture[Nothing] =
        TFuture.exception(new IllegalStateException("Streaming isn't supported"))

      override protected def handleWS[T](
          responseAs: GenericWebSocketResponseAs[T, _],
          meta: ResponseMetadata,
          ws: Nothing
      ): TFuture[T] = ws

      override protected def cleanupWhenNotAWebSocket(response: FResponse, e: NotAWebSocketException): TFuture[Unit] =
        TFuture.Done

      override protected def cleanupWhenGotWebSocket(response: Nothing, e: GotAWebSocketException): TFuture[Unit] =
        response
    }

  private def getClient(
      c: Option[Client],
      request: GenericRequest[_, Nothing]
  ): Service[http.Request, FResponse] = {
    val client = c.getOrElse {
      request.uri.scheme match {
        case Some("https") => Http.client.withTransport.tls
        case _             => Http.client
      }
    }

    val limitedClient =
      request.maxResponseBodyLength.fold(client)(l => client.withMaxResponseSize(util.StorageUnit.fromBytes(l)))

    val timeout = request.options.readTimeout
    if (timeout.isFinite) {
      limitedClient
        .withRequestTimeout(Duration.fromMilliseconds(timeout.toMillis))
        .newService(uriToFinagleDestination(request.uri))
    } else {
      limitedClient
        .withRequestTimeout(Duration.Top) // Finagle counterpart of Duration.Inf as far as I understand
        .newService(uriToFinagleDestination(request.uri))
    }
  }

  private def uriToFinagleDestination(uri: Uri): String = {
    val defaultPort = uri.scheme match {
      case Some("https") => 443
      case _             => 80
    }
    s"${uri.host.getOrElse("localhost")}:${uri.port.getOrElse(defaultPort)}"
  }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => TFuture[T]): TFuture[T] =
    SttpClientException.adjustExceptions(monad)(t)(exceptionToSttpClientException(request, _))

  private def exceptionToSttpClientException(request: GenericRequest[_, _], e: Exception): Option[Exception] =
    e match {
      case e: com.twitter.finagle.NoBrokersAvailableException =>
        Some(new SttpClientException.ConnectException(request, e))
      case e: com.twitter.finagle.Failure if e.getCause.isInstanceOf[com.twitter.finagle.ConnectionFailedException] =>
        Some(new SttpClientException.ConnectException(request, e))
      case e: com.twitter.finagle.ChannelClosedException => Some(new SttpClientException.ReadException(request, e))
      case e: com.twitter.finagle.IndividualRequestTimeoutException =>
        Some(new SttpClientException.TimeoutException(request, e))
      case e: com.twitter.finagle.http.TooLongMessageException =>
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

  def apply(): Backend[TFuture] =
    FollowRedirectsBackend(new FinagleBackend())

  def usingClient(client: Client): Backend[TFuture] =
    wrappers.FollowRedirectsBackend(new FinagleBackend(Some(client)))

  /** Create a stub backend for testing, which uses the [[TFuture]] response wrapper, and doesn't support streaming.
    *
    * See [[BackendStub]] for details on how to configure stub responses.
    */
  def stub: BackendStub[TFuture] = BackendStub(TFutureMonadError)
}
