package sttp.client.finagle

import com.twitter.finagle.Http.Client
import com.twitter.finagle.{Http, Service, http}
import sttp.client.{
  BasicRequestBody,
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  FollowRedirectsBackend,
  IgnoreResponse,
  InputStreamBody,
  MappedResponseAs,
  MultipartBody,
  NoBody,
  NothingT,
  Request,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseMetadata,
  StringBody,
  SttpBackend
}
import com.twitter.util.{Future => TFuture}
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import com.twitter.finagle.http.{
  FileElement,
  FormElement,
  RequestBuilder,
  SimpleElement,
  Method => FMethod,
  Response => FResponse
}
import com.twitter.io.Buf
import com.twitter.io.Buf.{ByteArray, ByteBuffer}
import com.twitter.util
import sttp.client.internal.FileHelpers
import sttp.model.{Header, Method, Part, StatusCode}
import com.twitter.util.Duration

import scala.io.Source

class FinagleBackend(client: Option[Client] = None) extends SttpBackend[TFuture, Nothing, NothingT] {
  override def send[T](request: Request[T, Nothing]): TFuture[Response[T]] = {
    val service = getClient(client, request)
    val finagleRequest = requestBodyToFinagle(request)
    service.apply(finagleRequest).flatMap { fResponse =>
      val code = StatusCode.unsafeApply(fResponse.statusCode)
      val headers = fResponse.headerMap.map(h => Header.notValidated(h._1, h._2)).toList
      val statusText = fResponse.status.reason
      val responseMetadata = ResponseMetadata(headers, code, statusText)
      val body = fromFinagleResponse(request.response, fResponse, responseMetadata)
      service.close().flatMap(_ => body.map(sttp.client.Response(_, code, statusText, headers, Nil)))
    }
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, Nothing],
      handler: NothingT[WS_RESULT]
  ): TFuture[WebSocketResponse[WS_RESULT]] = handler

  override def close(): TFuture[Unit] = TFuture.Done

  override def responseMonad: MonadError[TFuture] = new MonadError[TFuture] {
    override def unit[T](t: T): TFuture[T] = TFuture.apply(t)
    override def map[T, T2](fa: TFuture[T])(f: T => T2): TFuture[T2] = fa.map(f)
    override def flatMap[T, T2](fa: TFuture[T])(f: T => TFuture[T2]): TFuture[T2] = fa.flatMap(f)
    override def error[T](t: Throwable): TFuture[T] = TFuture.exception(t)
    override protected def handleWrappedError[T](rt: TFuture[T])(
        h: PartialFunction[Throwable, TFuture[T]]
    ): TFuture[T] = rt.rescue(h)
    override def eval[T](t: => T): TFuture[T] = TFuture(t)
  }

  private def headersToMap(headers: Seq[Header]): Map[String, String] = {
    headers.map(header => header.name -> header.value).toMap
  }

  private def methodToFinagle(m: Method): FMethod = m match {
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
        val requestBuilder = RequestBuilder.create().url(r.uri.toString).addHeaders(headersToMap(r.headers))
        val elements = parts.map { part =>
          getBasicBodyContent(part)
        }
        requestBuilder.add(elements).buildFormPost(true)
      //requestBuilder.addFormElement(elements: _*).buildFormPost(true)
      case _ => buildRequest(url, headers, finagleMethod, None)
    }
  }

  private def getBasicBodyContent(part: Part[BasicRequestBody]): FormElement = {

    val content: String = part.body match {
      case StringBody(s, _, _)    => s
      case ByteArrayBody(b, _)    => Source.fromBytes(b).mkString
      case ByteBufferBody(b, _)   => Source.fromBytes(b.array()).mkString
      case InputStreamBody(is, _) => Source.fromInputStream(is).mkString
      case FileBody(f, _)         => Source.fromFile(f.toFile).mkString
    }

    part.fileName match {
      case Some(_) => FileElement(part.name, ByteArray(content.getBytes: _*), part.contentType, part.fileName)
      case None    => SimpleElement(part.name, content)
    }

  }

  private def buildRequest(
      url: String,
      headers: Map[String, String],
      method: FMethod,
      content: Option[Buf]
  ): http.Request = {
    RequestBuilder.create().url(url).addHeaders(headers).build(method, content)
  }

  private def fromFinagleResponse[T](rr: ResponseAs[T, Nothing], r: FResponse, meta: ResponseMetadata): TFuture[T] = {

    rr match {
      case MappedResponseAs(raw, g) =>
        fromFinagleResponse(raw, r, meta).map(h => g(h, meta))

      case ResponseAsFromMetadata(f) => fromFinagleResponse(f(meta), r, meta)

      case IgnoreResponse =>
        TFuture(r.clearContent())

      case ResponseAsByteArray =>
        TFuture.const(util.Try {
          val bb = ByteBuffer.Owned.extract(r.content)
          val b = new Array[Byte](bb.remaining)
          bb.get(b)
          b
        })

      case ras @ ResponseAsStream() =>
        responseBodyToStream(r).map(ras.responseIsStream)

      case ResponseAsFile(file) =>
        val body = TFuture.const(util.Try(FileHelpers.saveFile(file.toFile, r.getInputStream())))
        body.map(_ => file)
    }
  }

  private def responseBodyToStream[T](r: FResponse): TFuture[T] =
    TFuture.exception(new IllegalStateException("Streaming isn't supported"))

  private def getClient(c: Option[Client], request: Request[_, Nothing]): Service[http.Request, FResponse] = {
    val client = c.getOrElse {
      request.uri.scheme match {
        case "https" => Http.client.withTransport.tls
        case _       => Http.client
      }
    }
    client
      .withRequestTimeout(Duration.fromMilliseconds(request.options.readTimeout.toMillis))
      .newService(s"${request.uri.host}:${request.uri.port.getOrElse(80)}")
  }
}

object FinagleBackend {

  def apply(): SttpBackend[TFuture, Nothing, NothingT] = {
    new FollowRedirectsBackend[TFuture, Nothing, NothingT](new FinagleBackend())
  }

  def usingClient(client: Client): SttpBackend[TFuture, Nothing, NothingT] = {
    new FollowRedirectsBackend[TFuture, Nothing, NothingT](new FinagleBackend(Some(client)))
  }
}
