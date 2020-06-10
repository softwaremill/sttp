package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Base64

import sttp.client.internal.DigestAuthenticator.DigestAuthData
import sttp.client.internal._
import sttp.client.internal.{SttpFile, ToCurlConverter}
import sttp.client.ws.WebSocketResponse
import sttp.model._

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import scala.language.higherKinds

/**
  * Describes a HTTP request, along with a description of how the response body should be handled.
  *
  * @param response Description of how the response body should be handled.
  *                 Needs to be specified upfront so that the response
  *                 is always consumed and hence there are no requirements on
  *                 client code to consume it. An exception to this are
  *                 streaming responses, which need to fully consumed by the
  *                 client if such a response type is requested.
  * @param tags Request-specific tags which can be used by backends for
  *             logging, metrics, etc. Not used by default.
  * @tparam U Specifies if the method & uri are specified. By default can be
  *           either:
  *           * [[Empty]], which is a type constructor which always resolves to
  *           [[None]]. This type of request is aliased to [[PartialRequest]]:
  *           there's no method and uri specified, and the request cannot be
  *           sent.
  *           * [[Identity]], which is an identity type constructor. This type of
  *           request is aliased to [[Request]]: the method and uri are
  *           specified, and the request can be sent.
  * @tparam T The target type, to which the response body should be read.
  */
case class RequestT[U[_], T, +S](
    method: U[Method],
    uri: U[Uri],
    body: RequestBody[S],
    headers: Seq[Header],
    response: ResponseAs[T, S],
    options: RequestOptions,
    tags: Map[String, Any]
) extends RequestTExtensions[U, T, S] {
  def get(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.GET)
  def head(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.HEAD)
  def post(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.POST)
  def put(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.PUT)
  def delete(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.DELETE)
  def options(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.OPTIONS)
  def patch(uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = Method.PATCH)
  def method(method: Method, uri: Uri): Request[T, S] =
    this.copy[Identity, T, S](uri = uri, method = method)

  def contentType(ct: String): RequestT[U, T, S] =
    header(HeaderNames.ContentType, ct, replaceExisting = true)
  def contentType(mt: MediaType): RequestT[U, T, S] =
    header(HeaderNames.ContentType, mt.toString, replaceExisting = true)
  def contentType(ct: String, encoding: String): RequestT[U, T, S] =
    header(HeaderNames.ContentType, contentTypeWithCharset(ct, encoding), replaceExisting = true)
  def contentLength(l: Long): RequestT[U, T, S] =
    header(HeaderNames.ContentLength, l.toString, replaceExisting = true)

  /**
    * Adds the given header to the end of the headers sequence.
    * @param replaceExisting If there's already a header with the same name, should it be dropped?
    */
  def header(h: Header, replaceExisting: Boolean = false): RequestT[U, T, S] = {
    val current = if (replaceExisting) headers.filterNot(_.is(h.name)) else headers
    this.copy(headers = current :+ h)
  }

  /**
    * Adds the given header to the end of the headers sequence.
    * @param replaceExisting If there's already a header with the same name, should it be dropped?
    */
  def header(k: String, v: String, replaceExisting: Boolean): RequestT[U, T, S] =
    header(Header(k, v), replaceExisting)
  def header(k: String, v: String): RequestT[U, T, S] = header(Header(k, v))
  def header(k: String, ov: Option[String]): RequestT[U, T, S] = ov.fold(this)(header(k, _))
  def headers(hs: Map[String, String]): RequestT[U, T, S] =
    headers(hs.map(t => Header(t._1, t._2)).toSeq: _*)
  def headers(hs: Header*): RequestT[U, T, S] = this.copy(headers = headers ++ hs)
  def auth: SpecifyAuthScheme[U, T, S] =
    new SpecifyAuthScheme[U, T, S](HeaderNames.Authorization, this, DigestAuthenticationBackend.DigestAuthTag)
  def proxyAuth: SpecifyAuthScheme[U, T, S] =
    new SpecifyAuthScheme[U, T, S](HeaderNames.ProxyAuthorization, this, DigestAuthenticationBackend.ProxyDigestAuthTag)
  def acceptEncoding(encoding: String): RequestT[U, T, S] =
    header(HeaderNames.AcceptEncoding, encoding, replaceExisting = true)

  def cookie(nv: (String, String)): RequestT[U, T, S] = cookies(nv)
  def cookie(n: String, v: String): RequestT[U, T, S] = cookies((n, v))
  def cookies(r: Response[_]): RequestT[U, T, S] = cookies(r.cookies.map(c => (c.name, c.value)): _*)
  def cookies(cs: Iterable[CookieWithMeta]): RequestT[U, T, S] = cookies(cs.map(c => (c.name, c.value)).toSeq: _*)
  def cookies(nvs: (String, String)*): RequestT[U, T, S] = {
    header(
      HeaderNames.Cookie,
      (headers.find(_.name == HeaderNames.Cookie).map(_.value).toSeq ++ nvs.map(p => p._1 + "=" + p._2)).mkString("; "),
      replaceExisting = true
    )
  }

  /**
    * Uses the `utf-8` encoding.
    *
    * If content type is not yet specified, will be set to `text/plain`
    * with `utf-8` encoding.
    *
    * If content length is not yet specified, will be set to the number of
    * bytes in the string using the `utf-8` encoding.
    */
  def body(b: String): RequestT[U, T, S] = body(b, Utf8)

  /**
    * If content type is not yet specified, will be set to `text/plain`
    * with the given encoding.
    *
    * If content length is not yet specified, will be set to the number of
    * bytes in the string using the given encoding.
    */
  def body(b: String, encoding: String): RequestT[U, T, S] =
    withBasicBody(StringBody(b, encoding))
      .setContentLengthIfMissing(b.getBytes(encoding).length.toLong)

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given array.
    */
  def body(b: Array[Byte]): RequestT[U, T, S] =
    withBasicBody(ByteArrayBody(b))
      .setContentLengthIfMissing(b.length.toLong)

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    */
  def body(b: ByteBuffer): RequestT[U, T, S] =
    withBasicBody(ByteBufferBody(b))

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    */
  def body(b: InputStream): RequestT[U, T, S] =
    withBasicBody(InputStreamBody(b))

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  private[client] def body(f: SttpFile): RequestT[U, T, S] =
    withBasicBody(FileBody(f))
      .setContentLengthIfMissing(f.size)

  /**
    * Encodes the given parameters as form data using `utf-8`.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: Map[String, String]): RequestT[U, T, S] =
    formDataBody(fs.toList, Utf8)

  /**
    * Encodes the given parameters as form data.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: Map[String, String], encoding: String): RequestT[U, T, S] =
    formDataBody(fs.toList, encoding)

  /**
    * Encodes the given parameters as form data using `utf-8`.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: (String, String)*): RequestT[U, T, S] =
    formDataBody(fs.toList, Utf8)

  /**
    * Encodes the given parameters as form data.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: Seq[(String, String)], encoding: String): RequestT[U, T, S] =
    formDataBody(fs, encoding)

  def multipartBody(ps: Seq[Part[BasicRequestBody]]): RequestT[U, T, S] =
    this.copy(body = MultipartBody(ps))

  def multipartBody(p1: Part[BasicRequestBody], ps: Part[BasicRequestBody]*): RequestT[U, T, S] =
    this.copy(body = MultipartBody(p1 :: ps.toList))

  def streamBody[S2 >: S](b: S2): RequestT[U, T, S2] =
    copy[U, T, S2](body = StreamBody(b))

  def readTimeout(t: Duration): RequestT[U, T, S] =
    this.copy(options = options.copy(readTimeout = t))

  /**
    * Specifies the target type to which the response body should be read.
    * Note that this replaces any previous specifications, which also includes
    * any previous `mapResponse` invocations.
    */
  def response[T2, S2 >: S](ra: ResponseAs[T2, S2]): RequestT[U, T2, S2] =
    this.copy(response = ra)

  def mapResponse[T2](f: T => T2): RequestT[U, T2, S] =
    this.copy(response = response.map(f))

  def followRedirects(fr: Boolean): RequestT[U, T, S] =
    this.copy(options = options.copy(followRedirects = fr))

  def maxRedirects(n: Int): RequestT[U, T, S] =
    if (n <= 0)
      this.copy(options = options.copy(followRedirects = false))
    else
      this.copy(options = options.copy(followRedirects = true, maxRedirects = n))

  def tag(k: String, v: Any): RequestT[U, T, S] =
    this.copy(tags = tags + (k -> v))

  def tag(k: String): Option[Any] = tags.get(k)

  /**
    * When a POST or PUT request is redirected, should the redirect be a POST/PUT as well (with the original body),
    * or should the request be converted to a GET without a body.
    *
    * Note that this only affects 301 and 302 redirects.
    * 303 redirects are always converted, while 307 and 308 redirects always keep the same method.
    *
    * See https://developer.mozilla.org/en-US/docs/Web/HTTP/Redirections for details.
    */
  def redirectToGet(r: Boolean): RequestT[U, T, S] =
    this.copy(options = options.copy(redirectToGet = r))

  /**
    * Sends the request, using the backend from the implicit scope. Only requests for which the method & URI are
    * specified can be sent.
    *
    * @return Depending on the backend, either the [[Response]] ([[Identity]] synchronous backends), or the
    *         [[Response]] in a backend-specific wrapper, or a failed effect (other backends).
    *
    *         The response body is deserialized as specified by this request (see [[RequestT.response]]).
    *
    *         Exceptions can be thrown directly ([[Identity]] synchronous backends), or wrapped (asynchronous backends).
    *         Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    *         unchanged.
    */
  def send[F[_]]()(implicit
      backend: SttpBackend[F, S, NothingT],
      isIdInRequest: IsIdInRequest[U]
  ): F[Response[T]] = backend.send(asRequest)

  /**
    * Opens a websocket, using the backend from the implicit scope. Only requests for which the method & URI are
    * specified can be sent. The backend must support handlers of the given type.
    *
    * @return Depending on the backend, either the [[WebSocketResponse]] ([[Identity]] synchronous backends), or the
    *         [[WebSocketResponse]] in a backend-specific wrapper, or a failed effect (other backends).
    *
    *         Exceptions can be thrown directly ([[Identity]] synchronous backends), or wrapped (asynchronous backends).
    *         Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    *         unchanged.
    */
  def openWebsocket[F[_], WS_HANDLER[_], WS_RESULT](
      handler: WS_HANDLER[WS_RESULT]
  )(implicit
      backend: SttpBackend[F, S, WS_HANDLER],
      isIdInRequest: IsIdInRequest[U]
  ): F[WebSocketResponse[WS_RESULT]] = backend.openWebsocket(asRequest, handler)

  def openWebsocketF[F[_], WS_HANDLER[_], WS_RESULT](
      handler: F[WS_HANDLER[WS_RESULT]]
  )(implicit
      backend: SttpBackend[F, S, WS_HANDLER],
      isIdInRequest: IsIdInRequest[U]
  ): F[WebSocketResponse[WS_RESULT]] =
    backend.responseMonad.flatMap(handler)(handler => backend.openWebsocket(asRequest, handler))

  def toCurl(implicit isIdInRequest: IsIdInRequest[U]): String = ToCurlConverter.requestToCurl(asRequest)

  private def asRequest(implicit isIdInRequest: IsIdInRequest[U]): RequestT[Identity, T, S] = {
    // we could avoid the asInstanceOf by creating an artificial copy
    // changing the method & url fields using `isIdInRequest`, but that
    // would be only to satisfy the type checker, and a needless copy at
    // runtime.
    this.asInstanceOf[RequestT[Identity, T, S]]
  }

  private def hasContentType: Boolean = headers.exists(_.is(HeaderNames.ContentType))
  private def setContentTypeIfMissing(mt: MediaType): RequestT[U, T, S] =
    if (hasContentType) this else contentType(mt)

  private[client] def withBasicBody(body: BasicRequestBody) = {
    val defaultCt = body match {
      case StringBody(_, encoding, Some(ct)) =>
        Some(ct.copy(charset = Some(encoding)))
      case _ =>
        body.defaultContentType
    }

    defaultCt.fold(this)(setContentTypeIfMissing).copy(body = body)
  }

  private def hasContentLength: Boolean =
    headers.exists(_.name.equalsIgnoreCase(HeaderNames.ContentLength))
  private def setContentLengthIfMissing(l: => Long): RequestT[U, T, S] =
    if (hasContentLength) this else contentLength(l)

  private def formDataBody(fs: Seq[(String, String)], encoding: String): RequestT[U, T, S] = {
    val b = RequestBody.paramsToStringBody(fs, encoding)
    setContentTypeIfMissing(MediaType.ApplicationXWwwFormUrlencoded)
      .setContentLengthIfMissing(b.s.getBytes(encoding).length.toLong)
      .copy(body = b)
  }
}

object RequestT {
  implicit class RichRequestTEither[U[_], L, R, S](r: RequestT[U, Either[L, R], S]) {
    def mapResponseRight[R2](f: R => R2): RequestT[U, Either[L, R2], S] = r.copy(response = r.response.mapRight(f))
  }
}

class SpecifyAuthScheme[U[_], T, +S](hn: String, rt: RequestT[U, T, S], digestTag: String) {
  def basic(user: String, password: String): RequestT[U, T, S] = {
    val c = new String(Base64.getEncoder.encode(s"$user:$password".getBytes(Utf8)), Utf8)
    rt.header(hn, s"Basic $c")
  }

  def basicToken(token: String): RequestT[U, T, S] =
    rt.header(hn, s"Basic $token")

  def bearer(token: String): RequestT[U, T, S] =
    rt.header(hn, s"Bearer $token")

  def digest(user: String, password: String): RequestT[U, T, S] = {
    rt.tag(digestTag, DigestAuthData(user, password))
  }
}

case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration,
    maxRedirects: Int,
    redirectToGet: Boolean
)
