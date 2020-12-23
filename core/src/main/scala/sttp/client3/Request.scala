package sttp.client3

import sttp.capabilities.{Effect, Streams}
import sttp.client3.internal.DigestAuthenticator.DigestAuthData
import sttp.client3.internal.{SttpFile, ToCurlConverter, Utf8, contentTypeWithCharset}
import sttp.model.{CookieWithMeta, HasHeaders, Header, HeaderNames, MediaType, Method, Part, Uri}

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Base64
import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration

/** Describes an HTTP request, along with a description of how the response body should be handled.
  *
  * The request can be sent using a [[SttpBackend]], which provides a superset of the required capabilities.
  *
  * @param response Description of how the response body should be handled.
  *                 Needs to be specified upfront so that the response
  *                 is always consumed and hence there are no requirements on
  *                 client code to consume it. An exception to this are
  *                 unsafe streaming and websocket responses, which need to
  *                 be consumed/closed by the client.
  * @param tags Request-specific tags which can be used by backends for
  *             logging, metrics, etc. Not used by default.
  * @tparam T The target type, to which the response body should be read.
  * @tparam R The backend capabilities required by the request or response description. This might be `Any` (no
  *           requirements), [[Effect]] (the backend must support the given effect type), [[Streams]] (the ability to
  *           send and receive streaming bodies) or [[sttp.capabilities.WebSockets]] (the ability to handle websocket
  *           requests).
  */
case class Request[T, -R](
    method: Method,
    uri: Uri,
    body: RequestBody[R],
    headers: Seq[Header],
    response: ResponseAs[T, R],
    options: RequestOptions,
    tags: Map[String, Any]
) extends RequestOps[T, R]
    with RequestMetadata { // TODO: toString

  override type ThisType[_T, -_R] = Request[_T, _R]

  override protected def withBody[R2](body: RequestBody[R2]): Request[T, R with R2] = copy(body = body)
  override protected def withHeaders(headers: Seq[Header]): Request[T, R] = copy(headers = headers)
  override protected def withResponseAs[T2, R2](responseAs: ResponseAs[T2, R2]): Request[T2, R with R2] =
    copy(response = responseAs)
  override protected def withOptions(options: RequestOptions): Request[T, R] = copy(options = options)
  override protected def withTags(tags: Map[String, Any]): Request[T, R] = copy(tags = tags)
  override protected def self: Request[T, R] = this

  /** Sends the request, using the backend from the implicit scope. Only requests for which the method & URI are
    * specified can be sent.
    *
    * The required capabilities must be a subset of the capabilities provided by the backend.
    *
    * @return For synchronous backends (when the effect type is [[Identity]]), [[Response]] is returned directly
    *         and exceptions are thrown.
    *         For asynchronous backends (when the effect type is e.g. [[scala.concurrent.Future]]), an effect containing
    *         the [[Response]] is returned. Exceptions are represented as failed effects (e.g. failed futures).
    *
    *         The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    *         Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    *         unchanged.
    */
  @deprecated(message = "use request.send(backend), providing the backend explicitly", since = "3.0.0")
  def send[F[_], P]()(implicit
      pEffectFIsR: P with Effect[F] <:< R,
      backend: SttpBackend[F, P]
  ): F[Response[T]] =
    send(backend)(pEffectFIsR) // the order of implicits must be different so that the signatures are different

  /** Sends the request, using the given backend. Only requests for which the method & URI are specified can be sent.
    *
    * The required capabilities must be a subset of the capabilities provided by the backend.
    *
    * @return For synchronous backends (when the effect type is [[Identity]]), [[Response]] is returned directly
    *         and exceptions are thrown.
    *         For asynchronous backends (when the effect type is e.g. [[scala.concurrent.Future]]), an effect containing
    *         the [[Response]] is returned. Exceptions are represented as failed effects (e.g. failed futures).
    *
    *         The response body is deserialized as specified by this request (see [[Request.response]]).
    *
    *         Known exceptions are converted by backends to one of [[SttpClientException]]. Other exceptions are thrown
    *         unchanged.
    */
  def send[F[_], P](backend: SttpBackend[F, P])(implicit
      pEffectFIsR: P with Effect[F] <:< R
  ): F[Response[T]] = backend.send(this.asInstanceOf[Request[T, P with Effect[F]]]) // as witnessed by pEffectFIsR

  def toCurl: String = ToCurlConverter.requestToCurl(this)

  override def showBasic: String = {
    val ws = if (isWebSocket) " (web socket) " else ""
    s"$method$ws $uri"
  }

  override def show(
      includeBody: Boolean = true,
      includeHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): String = doShow(Some(method), Some(uri), includeBody, includeHeaders, sensitiveHeaders)

  private[client3] def onlyMetadata: RequestMetadata = {
    val m = method
    val u = uri
    val h = headers
    new RequestMetadata {
      override val method: Method = m
      override val uri: Uri = u
      override val headers: Seq[Header] = h
    }
  }
}

object Request {
  implicit class RichRequestEither[A, B, R](r: Request[Either[A, B], R]) {
    def mapResponseRight[B2](f: B => B2): Request[Either[A, B2], R] = r.copy(response = r.response.mapRight(f))
    def getResponseRight: Request[B, R] = r.copy(response = r.response.getRight)
  }

  implicit class RichRequestEitherResponseException[HE, DE, B, R](
      r: Request[Either[ResponseException[HE, DE], B], R]
  ) {
    def getResponseEither: Request[Either[HE, B], R] = r.copy(response = r.response.getEither)
  }
}

/** A [[RequestT]] without the method & uri specified (which cannot yet be sent).
  */
case class PartialRequest[T, -R](
    body: RequestBody[R],
    headers: Seq[Header],
    response: ResponseAs[T, R],
    options: RequestOptions,
    tags: Map[String, Any]
) extends RequestOps[T, R] {
  override type ThisType[_T, -_R] = PartialRequest[_T, _R]

  override protected def withBody[R2](body: RequestBody[R2]): PartialRequest[T, R with R2] = copy(body = body)
  override protected def withHeaders(headers: Seq[Header]): PartialRequest[T, R] = copy(headers = headers)
  override protected def withResponseAs[T2, R2](responseAs: ResponseAs[T2, R2]): PartialRequest[T2, R with R2] =
    copy(response = responseAs)
  override protected def withOptions(options: RequestOptions): PartialRequest[T, R] = copy(options = options)
  override protected def withTags(tags: Map[String, Any]): PartialRequest[T, R] = copy(tags = tags)
  override protected def self: PartialRequest[T, R] = this

  override def showBasic: String = "(no method & uri set)"

  override def show(
      includeBody: Boolean = true,
      includeHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): String = doShow(None, None, includeBody, includeHeaders, sensitiveHeaders)
}

object PartialRequest { // TODO: copy-paste
  implicit class RichPartialRequestEither[A, B, R](r: PartialRequest[Either[A, B], R]) {
    def mapResponseRight[B2](f: B => B2): PartialRequest[Either[A, B2], R] = r.copy(response = r.response.mapRight(f))
    def getResponseRight: PartialRequest[B, R] = r.copy(response = r.response.getRight)
  }

  implicit class RichPartialRequestEitherResponseException[HE, DE, B, R](
      r: PartialRequest[Either[ResponseException[HE, DE], B], R]
  ) {
    def getResponseEither: PartialRequest[Either[HE, B], R] = r.copy(response = r.response.getEither)
  }
}

trait RequestOps[T, -R] extends HasHeaders with RequestOpsExtensions[T, R] {

  type ThisType[_T, -_R] <: RequestOps[_T, _R]

  def body: RequestBody[R]
  def headers: Seq[Header]
  def response: ResponseAs[T, R]
  def options: RequestOptions
  def tags: Map[String, Any]

  protected def withBody[R2](body: RequestBody[R2]): ThisType[T, R with R2]
  protected def withHeaders(headers: Seq[Header]): ThisType[T, R]
  protected def withResponseAs[T2, R2](responseAs: ResponseAs[T2, R2]): ThisType[T2, R with R2]
  protected def withOptions(options: RequestOptions): ThisType[T, R]
  protected def withTags(tags: Map[String, Any]): ThisType[T, R]
  protected def self: ThisType[T, R] // ThisType version of this

  def get(uri: Uri): Request[T, R] = method(Method.GET, uri)
  def head(uri: Uri): Request[T, R] = method(Method.HEAD, uri)
  def post(uri: Uri): Request[T, R] = method(Method.POST, uri)
  def put(uri: Uri): Request[T, R] = method(Method.PUT, uri)
  def delete(uri: Uri): Request[T, R] = method(Method.DELETE, uri)
  def options(uri: Uri): Request[T, R] = method(Method.OPTIONS, uri)
  def patch(uri: Uri): Request[T, R] = method(Method.PATCH, uri)
  def method(method: Method, uri: Uri): Request[T, R] = Request(method, uri, body, headers, response, options, tags)

  def contentType(ct: String): ThisType[T, R] = header(HeaderNames.ContentType, ct, replaceExisting = true)
  def contentType(mt: MediaType): ThisType[T, R] = header(HeaderNames.ContentType, mt.toString, replaceExisting = true)
  def contentType(ct: String, encoding: String): ThisType[T, R] =
    header(HeaderNames.ContentType, contentTypeWithCharset(ct, encoding), replaceExisting = true)
  def contentLength(l: Long): ThisType[T, R] = header(HeaderNames.ContentLength, l.toString, replaceExisting = true)

  /** Adds the given header to the end of the headers sequence.
    * @param replaceExisting If there's already a header with the same name, should it be dropped?
    */
  def header(h: Header, replaceExisting: Boolean = false): ThisType[T, R] = {
    val current = if (replaceExisting) headers.filterNot(_.is(h.name)) else headers
    withHeaders(current :+ h)
  }

  /** Adds the given header to the end of the headers sequence.
    * @param replaceExisting If there's already a header with the same name, should it be dropped?
    */
  def header(k: String, v: String, replaceExisting: Boolean): ThisType[T, R] = header(Header(k, v), replaceExisting)
  def header(k: String, v: String): ThisType[T, R] = header(Header(k, v))
  def header(k: String, ov: Option[String]): ThisType[T, R] =
    ov.fold(self)(header(k, _)) // TODO: remove header if None?
  def headers(hs: Map[String, String]): ThisType[T, R] = headers(hs.map(t => Header(t._1, t._2)).toSeq: _*)
  def headers(hs: Header*): ThisType[T, R] = withHeaders(headers ++ hs)
  def auth: SpecifyAuthScheme =
    new SpecifyAuthScheme(HeaderNames.Authorization, DigestAuthenticationBackend.DigestAuthTag)
  def proxyAuth: SpecifyAuthScheme =
    new SpecifyAuthScheme(HeaderNames.ProxyAuthorization, DigestAuthenticationBackend.ProxyDigestAuthTag)
  def acceptEncoding(encoding: String): ThisType[T, R] =
    header(HeaderNames.AcceptEncoding, encoding, replaceExisting = true)

  def cookie(nv: (String, String)): ThisType[T, R] = cookies(nv)
  def cookie(n: String, v: String): ThisType[T, R] = cookies((n, v))
  def cookies(r: Response[_]): ThisType[T, R] = cookies(r.cookies.map(c => (c.name, c.value)): _*)
  def cookies(cs: Iterable[CookieWithMeta]): ThisType[T, R] = cookies(cs.map(c => (c.name, c.value)).toSeq: _*)
  def cookies(nvs: (String, String)*): ThisType[T, R] = {
    header(
      HeaderNames.Cookie,
      (headers.find(_.name == HeaderNames.Cookie).map(_.value).toSeq ++ nvs.map(p => p._1 + "=" + p._2)).mkString("; "),
      replaceExisting = true
    )
  }

  /** Uses the `utf-8` encoding.
    *
    * If content type is not yet specified, will be set to `text/plain`
    * with `utf-8` encoding.
    *
    * If content length is not yet specified, will be set to the number of
    * bytes in the string using the `utf-8` encoding.
    */
  def body(b: String): ThisType[T, R] = body(b, Utf8)

  /** If content type is not yet specified, will be set to `text/plain`
    * with the given encoding.
    *
    * If content length is not yet specified, will be set to the number of
    * bytes in the string using the given encoding.
    */
  def body(b: String, encoding: String): ThisType[T, R] =
    withBodySetContentTypeIfMissing[R](StringBody(b, encoding))
      .setContentLengthIfMissing(
        b.getBytes(encoding).length.toLong
      )
      .asInstanceOf[ThisType[T, R]] // TODO

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given array.
    */
  def body(b: Array[Byte]): ThisType[T, R] =
    withBodySetContentTypeIfMissing[R](ByteArrayBody(b))
      .setContentLengthIfMissing(b.length.toLong)
      .asInstanceOf[ThisType[T, R]] // TODO

  /** If content type is not yet specified, will be set to `application/octet-stream`. */
  def body(b: ByteBuffer): ThisType[T, R] = withBodySetContentTypeIfMissing[R](ByteBufferBody(b))

  /** If content type is not yet specified, will be set to `application/octet-stream`. */
  def body(b: InputStream): ThisType[T, R] = withBodySetContentTypeIfMissing[R](InputStreamBody(b))

  /** If content type is not yet specified, will be set to  `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given file.
    */
  private[client3] def body(f: SttpFile): ThisType[T, R] =
    withBodySetContentTypeIfMissing[R](FileBody(f))
      .setContentLengthIfMissing(f.size)
      .asInstanceOf[ThisType[T, R]] // TODO

  /** Encodes the given parameters as form data using `utf-8`.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: Map[String, String]): ThisType[T, R] = formDataBody(fs.toList, Utf8)

  /** Encodes the given parameters as form data.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: Map[String, String], encoding: String): ThisType[T, R] = formDataBody(fs.toList, encoding)

  /** Encodes the given parameters as form data using `utf-8`.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: (String, String)*): ThisType[T, R] = formDataBody(fs.toList, Utf8)

  /** Encodes the given parameters as form data.
    * If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length
    * of the number of bytes in the url-encoded parameter string.
    */
  def body(fs: Seq[(String, String)], encoding: String): ThisType[T, R] = formDataBody(fs, encoding)

  def multipartBody[R2](ps: Seq[Part[RequestBody[R2]]]): ThisType[T, R with R2] = withBody(MultipartBody(ps))

  def multipartBody[R2](p1: Part[RequestBody[R2]], ps: Part[RequestBody[R2]]*): ThisType[T, R with R2] = withBody(
    MultipartBody(p1 :: ps.toList)
  )

  def streamBody[S](s: Streams[S])(b: s.BinaryStream): ThisType[T, R with S] = withBodySetContentTypeIfMissing(
    StreamBody(s)(b)
  )

  def readTimeout(t: Duration): ThisType[T, R] = withOptions(options.copy(readTimeout = t))

  /** Specifies the target type to which the response body should be read.
    * Note that this replaces any previous specifications, which also includes
    * any previous `mapResponse` invocations.
    */
  def response[T2, R2](ra: ResponseAs[T2, R2]): ThisType[T2, R with R2] = withResponseAs(ra)

  def mapResponse[T2](f: T => T2): ThisType[T2, R] = withResponseAs(response.map(f))

  def isWebSocket: Boolean = ResponseAs.isWebSocket(response)

  def followRedirects(fr: Boolean): ThisType[T, R] = withOptions(options.copy(followRedirects = fr))

  def maxRedirects(n: Int): ThisType[T, R] =
    if (n <= 0)
      withOptions(options.copy(followRedirects = false))
    else
      withOptions(options.copy(followRedirects = true, maxRedirects = n))

  def tag(k: String, v: Any): ThisType[T, R] = withTags(tags + (k -> v))

  def tag(k: String): Option[Any] = tags.get(k)

  /** When a POST or PUT request is redirected, should the redirect be a POST/PUT as well (with the original body),
    * or should the request be converted to a GET without a body.
    *
    * Note that this only affects 301 and 302 redirects.
    * 303 redirects are always converted, while 307 and 308 redirects always keep the same method.
    *
    * See https://developer.mozilla.org/en-US/docs/Web/HTTP/Redirections for details.
    */
  def redirectToGet(r: Boolean): ThisType[T, R] = withOptions(options.copy(redirectToGet = r))

  def showBasic: String

  def show(
      includeBody: Boolean = true,
      includeHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): String

  protected def doShow(
      method: Option[Method],
      uri: Option[Uri],
      includeBody: Boolean,
      includeHeaders: Boolean,
      sensitiveHeaders: Set[String]
  ): String = {
    val headers =
      if (includeHeaders) ", headers: " + this.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(", ") else ""
    val body = if (includeBody) s", body: ${this.body.show}" else ""
    val methodAndUri = (method, uri) match {
      case (Some(m), Some(u)) =>
        val ws = if (isWebSocket) " (web socket) " else ""
        s"$m$ws $u, "
      case _ => ""
    }
    s"${methodAndUri}response as: ${response.show}$headers$body"
  }

  private def hasContentType: Boolean = headers.exists(_.is(HeaderNames.ContentType))
  private def setContentTypeIfMissing(mt: MediaType): ThisType[T, R] = if (hasContentType) self else contentType(mt)

  protected def withBodySetContentTypeIfMissing[R2](
      body: RequestBody[R2]
  ): ThisType[T, R with R2] = {
    val defaultCt = body match {
      case StringBody(_, encoding, ct) =>
        ct.copy(charset = Some(encoding))
      case _ =>
        body.defaultContentType
    }

    setContentTypeIfMissing(defaultCt).withBody[R2](body).asInstanceOf[ThisType[T, R]] // TODO
  }

  private def hasContentLength: Boolean = headers.exists(_.name.equalsIgnoreCase(HeaderNames.ContentLength))
  private def setContentLengthIfMissing(l: => Long): ThisType[T, R] = if (hasContentLength) self else contentLength(l)

  private def formDataBody(fs: Seq[(String, String)], encoding: String): ThisType[T, R] = {
    val b = RequestBody.paramsToStringBody(fs, encoding)
    setContentTypeIfMissing(MediaType.ApplicationXWwwFormUrlencoded)
      .setContentLengthIfMissing(b.s.getBytes(encoding).length.toLong)
      .withBody[R](b)
      .asInstanceOf[ThisType[T, R]] // TODO
  }

  class SpecifyAuthScheme(hn: String, digestTag: String) {
    def basic(user: String, password: String): ThisType[T, R] = {
      val c = new String(Base64.getEncoder.encode(s"$user:$password".getBytes(Utf8)), Utf8)
      header(hn, s"Basic $c")
    }

    def basicToken(token: String): ThisType[T, R] = header(hn, s"Basic $token")
    def bearer(token: String): ThisType[T, R] = header(hn, s"Bearer $token")
    def digest(user: String, password: String): ThisType[T, R] = tag(digestTag, DigestAuthData(user, password))
  }
}

case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration,
    maxRedirects: Int,
    redirectToGet: Boolean
)
