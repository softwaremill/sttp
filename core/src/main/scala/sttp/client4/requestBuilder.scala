package sttp.client4

import sttp.client4.internal.{ContentEncoding, SttpFile, Utf8, contentTypeWithCharset}
import sttp.client4.logging.LoggingOptions
import sttp.client4.wrappers.DigestAuthenticationBackend
import sttp.model.HasHeaders
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.HttpVersion
import sttp.model.MediaType
import sttp.model.Method
import sttp.model.Part
import sttp.model.Uri
import sttp.model.headers.CookieWithMeta

import java.io.InputStream
import java.nio.ByteBuffer
import scala.concurrent.duration.Duration
import scala.collection.immutable.Seq

/** The builder methods of requests or partial requests of type `PR`.
  *
  * @tparam PR
  *   The type of the request or partial request. The method and uri may not be specified yet.
  * @tparam R
  *   The type of request when the method and uri are specified.
  */
trait PartialRequestBuilder[+PR <: PartialRequestBuilder[PR, R], +R]
    extends HasHeaders
    with PartialRequestExtensions[PR] {
  self: PR =>

  def showBasic: String

  def headers: Seq[Header]
  def body: GenericRequestBody[_]

  /** Description of how the response body should be handled. Needs to be specified upfront so that the response is
    * always consumed and hence there are no requirements on client code to consume it.
    */
  def response: ResponseAsDelegate[_, _]
  def options: RequestOptions

  /** Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default. */
  def tags: Map[String, Any]

  /** Set the method & uri to the given ones. */
  def method(method: Method, uri: Uri): R

  /** Replace all headers with the given ones. */
  def withHeaders(headers: Seq[Header]): PR

  /** Replace all options with the given ones. */
  def withOptions(options: RequestOptions): PR

  /** Replace all tags with the given ones. */
  def withTags(tags: Map[String, Any]): PR

  protected def copyWithBody(body: BasicBody): PR

  def get(uri: Uri): R = method(Method.GET, uri)
  def head(uri: Uri): R = method(Method.HEAD, uri)
  def post(uri: Uri): R = method(Method.POST, uri)
  def put(uri: Uri): R = method(Method.PUT, uri)
  def delete(uri: Uri): R = method(Method.DELETE, uri)
  def options(uri: Uri): R = method(Method.OPTIONS, uri)
  def patch(uri: Uri): R = method(Method.PATCH, uri)

  def contentType(ct: String): PR =
    header(HeaderNames.ContentType, ct, replaceExisting = true)
  def contentType(mt: MediaType): PR =
    header(HeaderNames.ContentType, mt.toString, replaceExisting = true)
  def contentType(ct: String, encoding: String): PR =
    header(HeaderNames.ContentType, contentTypeWithCharset(ct, encoding), replaceExisting = true)
  def contentLength(l: Long): PR =
    header(HeaderNames.ContentLength, l.toString, replaceExisting = true)

  def contentEncoding(encoding: ContentEncoding): PR =
    header(HeaderNames.ContentEncoding, encoding.name, replaceExisting = false)
      .withOptions(options.copy(encoding = options.encoding :+ encoding))

  /** Adds the given header to the end of the headers sequence.
    * @param replaceExisting
    *   If there's already a header with the same name, should it be replaced?
    */
  def header(h: Header, replaceExisting: Boolean = false): PR = {
    val current = if (replaceExisting) headers.filterNot(_.is(h.name)) else headers
    withHeaders(headers = current :+ h)
  }

  /** Adds the given header to the end of the headers sequence.
    * @param replaceExisting
    *   If there's already a header with the same name, should it be replaced?
    */
  def header(k: String, v: String, replaceExisting: Boolean): PR =
    header(Header(k, v), replaceExisting)

  /** Adds the given header to the end of the headers sequence. */
  def header(k: String, v: String): PR = header(Header(k, v))

  /** Adds the given header to the end of the headers sequence, if the value is defined. Otherwise has no effect. */
  def header(k: String, ov: Option[String]): PR = ov.fold(this)(header(k, _))

  /** Adds the given headers to the end of the headers sequence. */
  def headers(hs: Map[String, String]): PR = headers(hs.map(t => Header(t._1, t._2)).toSeq: _*)

  /** Adds the given headers to the end of the headers sequence.
    * @param replaceExisting
    *   If there's already a header with the same name, should it be replaced?
    */
  def headers(hs: Map[String, String], replaceExisting: Boolean): PR =
    if (replaceExisting) hs.foldLeft(this)((s, h) => s.header(h._1, h._2, replaceExisting))
    else headers(hs)

  /** Adds the given headers to the end of the headers sequence. */
  def headers(hs: Header*): PR = withHeaders(headers = headers ++ hs)

  /** Adds the given headers to the end of the headers sequence. */
  def headers(hs: Seq[Header], replaceExisting: Boolean): PR =
    if (replaceExisting) hs.foldLeft(this)((s, h) => s.header(h, replaceExisting))
    else headers(hs: _*)

  def auth: SpecifyAuthScheme[PR] =
    new SpecifyAuthScheme[PR](HeaderNames.Authorization, this, DigestAuthenticationBackend.DigestAuthTag)
  def proxyAuth: SpecifyAuthScheme[PR] =
    new SpecifyAuthScheme[PR](HeaderNames.ProxyAuthorization, this, DigestAuthenticationBackend.ProxyDigestAuthTag)
  def acceptEncoding(encoding: String): PR =
    header(HeaderNames.AcceptEncoding, encoding, replaceExisting = true)

  def cookie(nv: (String, String)): PR = cookies(nv)
  def cookie(n: String, v: String): PR = cookies((n, v))
  def cookies(r: Response[_]): PR = cookies(r.cookies.collect { case Right(c) => c }.map(c => (c.name, c.value)): _*)
  def cookies(cs: Iterable[CookieWithMeta]): PR = cookies(cs.map(c => (c.name, c.value)).toSeq: _*)
  def cookies(nvs: (String, String)*): PR =
    header(
      HeaderNames.Cookie,
      (headers.find(_.name == HeaderNames.Cookie).map(_.value).toSeq ++ nvs.map(p => p._1 + "=" + p._2)).mkString("; "),
      replaceExisting = true
    )

  private[client4] def hasContentType: Boolean = headers.exists(_.is(HeaderNames.ContentType))
  private[client4] def setContentTypeIfMissing(mt: MediaType): PR =
    if (hasContentType) this else contentType(mt)

  private[client4] def hasContentLength: Boolean =
    headers.exists(_.name.equalsIgnoreCase(HeaderNames.ContentLength))
  private[client4] def setContentLengthIfMissing(l: => Long): PR =
    if (hasContentLength) this else contentLength(l)

  /** Uses the `utf-8` encoding.
    *
    * If content type is not yet specified, will be set to `text/plain` with `utf-8` encoding.
    *
    * If content length is not yet specified, will be set to the number of bytes in the string using the `utf-8`
    * encoding.
    */
  def body(b: String): PR = body(b, Utf8)

  /** If content type is not yet specified, will be set to `text/plain` with the given encoding.
    *
    * If content length is not yet specified, will be set to the number of bytes in the string using the given encoding.
    */
  def body(b: String, encoding: String): PR =
    withBody(StringBody(b, encoding)).setContentLengthIfMissing(b.getBytes(encoding).length.toLong)

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given array.
    */
  def body(b: Array[Byte]): PR = withBody(ByteArrayBody(b)).setContentLengthIfMissing(b.length.toLong)

  /** If content type is not yet specified, will be set to `application/octet-stream`. */
  def body(b: ByteBuffer): PR = withBody(ByteBufferBody(b))

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    */
  def body(b: InputStream): PR = withBody(InputStreamBody(b))

  /** If content type is not yet specified, will be set to `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length of the given file.
    */
  private[client4] def body(f: SttpFile): PR = withBody(FileBody(f)).setContentLengthIfMissing(f.size)

  /** Encodes the given parameters as form data using `utf-8`. If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length of the number of bytes in the url-encoded
    * parameter string.
    */
  def body(fs: Map[String, String]): PR = formDataBody(fs.toList, Utf8)

  /** Encodes the given parameters as form data. If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length of the number of bytes in the url-encoded
    * parameter string.
    */
  def body(fs: Map[String, String], encoding: String): PR = formDataBody(fs.toList, encoding)

  /** Encodes the given parameters as form data using `utf-8`. If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length of the number of bytes in the url-encoded
    * parameter string.
    */
  def body(fs: (String, String)*): PR = formDataBody(fs.toList, Utf8)

  /** Encodes the given parameters as form data. If content type is not yet specified, will be set to
    * `application/x-www-form-urlencoded`.
    *
    * If content length is not yet specified, will be set to the length of the number of bytes in the url-encoded
    * parameter string.
    */
  def body(fs: Seq[(String, String)], encoding: String): PR = formDataBody(fs, encoding)

  def multipartBody(ps: Seq[Part[BasicBodyPart]]): PR = copyWithBody(BasicMultipartBody(ps))

  def multipartBody(p1: Part[BasicBodyPart], ps: Part[BasicBodyPart]*): PR = copyWithBody(
    BasicMultipartBody(p1 :: ps.toList)
  )

  private def formDataBody(fs: Seq[(String, String)], encoding: String): PR = {
    val b = BasicBody.paramsToStringBody(fs, encoding)
    copyWithBody(b)
      .setContentTypeIfMissing(MediaType.ApplicationXWwwFormUrlencoded)
      .setContentLengthIfMissing(b.s.getBytes(encoding).length.toLong)
  }

  def withBody(body: BasicBody): PR = {
    val defaultCt = body match {
      case StringBody(_, encoding, ct) =>
        ct.copy(charset = Some(encoding))
      case _ =>
        body.defaultContentType
    }

    copyWithBody(body).setContentTypeIfMissing(defaultCt)
  }

  /** When the request is sent, if reading the response times out (there's no activity for the given period of time), a
    * failed effect will be returned, or an exception will be thrown
    */
  def readTimeout(t: Duration): PR = withOptions(options.copy(readTimeout = t))

  def followRedirects(fr: Boolean): PR = withOptions(options.copy(followRedirects = fr))

  def maxRedirects(n: Int): PR =
    if (n <= 0) withOptions(options.copy(followRedirects = false))
    else withOptions(options.copy(followRedirects = true, maxRedirects = n))

    /** When a POST or PUT request is redirected, should the redirect be a POST/PUT as well (with the original body), or
      * should the request be converted to a GET without a body.
      *
      * Note that this only affects 301 and 302 redirects. 303 redirects are always converted, while 307 and 308
      * redirects always keep the same method.
      *
      * See https://developer.mozilla.org/en-US/docs/Web/HTTP/Redirections for details.
      */
  def redirectToGet(r: Boolean): PR = withOptions(options.copy(redirectToGet = r))

  def tag(k: String, v: Any): PR = withTags(tags + (k -> v))

  def tag(k: String): Option[Any] = tags.get(k)

  private val disableAutoDecompressionKey = "disableAutoDecompression"

  // Used as a workaround to keep binary compatibility
  // TODO: replace with additional parameter in RequestOptions when writing sttp4
  def disableAutoDecompression: PR = tag(disableAutoDecompressionKey, true)

  def autoDecompressionDisabled: Boolean = tags.getOrElse(disableAutoDecompressionKey, false).asInstanceOf[Boolean]

  private val httpVersionKey = "httpVersion"

  // Used as a workaround to keep binary compatibility
  // TODO: replace with additional parameter in RequestOptions when writing sttp4
  // TODO: add similar functionality to Response

  /** Allows setting HTTP version per request. Supported only is a few backends
    *
    * @param version:
    *   one of values from [[HttpVersion]] enum.
    * @return
    *   request with version tag
    */
  def httpVersion(version: HttpVersion): PR = tag(httpVersionKey, version)

  /** Get[[HttpVersion]] from tags in request. Supported only is a few backends
    *
    * @return
    *   one of values form [[HttpVersion]] enum or [[None]]
    */
  def httpVersion: Option[HttpVersion] = tags.get(httpVersionKey).map(_.asInstanceOf[HttpVersion])

  private val loggingOptionsTagKey = "loggingOptions"

  /** Will only have effect when using the `LoggingBackend` */
  def logSettings(
      logRequestBody: Option[Boolean] = None,
      logResponseBody: Option[Boolean] = None,
      logRequestHeaders: Option[Boolean] = None,
      logResponseHeaders: Option[Boolean] = None
  ): PR = {
    val loggingOptions = LoggingOptions(
      logRequestBody = logRequestBody,
      logResponseBody = logResponseBody,
      logRequestHeaders = logRequestHeaders,
      logResponseHeaders = logResponseHeaders
    )
    this.tag(loggingOptionsTagKey, loggingOptions)
  }

  def logSettings(
      loggingOptions: Option[LoggingOptions]
  ): PR =
    this.tag(loggingOptionsTagKey, loggingOptions)

  def loggingOptions: Option[LoggingOptions] = tag(loggingOptionsTagKey).asInstanceOf[Option[LoggingOptions]]

  def show(
      includeBody: Boolean = true,
      includeHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): String = {
    val headers =
      if (includeHeaders) ", headers: " + this.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(", ") else ""
    val body = if (includeBody) s", body: ${this.body.show}" else ""
    s"$showBasic, response as: ${response.show}$headers$body"
  }
}

/** Describes a partial HTTP request, along with a description of how the response body should be handled. A partial
  * request cannot be sent because the method and uri are not yet specified.
  *
  * @param response
  *   Description of how the response body should be handled. Needs to be specified upfront so that the response is
  *   always consumed and hence there are no requirements on client code to consume it.
  * @param tags
  *   Request-specific tags which can be used by backends for logging, metrics, etc. Empty by default.
  * @tparam T
  *   The target type, to which the response body should be read.
  */
final case class PartialRequest[T](
    body: BasicBody,
    headers: Seq[Header],
    response: ResponseAs[T],
    options: RequestOptions,
    tags: Map[String, Any]
) extends PartialRequestBuilder[PartialRequest[T], Request[T]] {

  override def showBasic: String = "(no method & uri set)"

  override def method(method: Method, uri: Uri): Request[T] =
    Request(method, uri, body, headers, response, options, tags)
  override def withHeaders(headers: Seq[Header]): PartialRequest[T] = copy(headers = headers)
  override def withOptions(options: RequestOptions): PartialRequest[T] = copy(options = options)
  override def withTags(tags: Map[String, Any]): PartialRequest[T] = copy(tags = tags)
  override protected def copyWithBody(body: BasicBody): PartialRequest[T] = copy(body = body)
  def response[T2](ra: ResponseAs[T2]): PartialRequest[T2] = copy(response = ra)
}

/** The builder methods of a request. The uri and method are specified.
  *
  * @tparam R
  *   The type of request
  */
trait RequestBuilder[+R <: RequestBuilder[R]] extends PartialRequestBuilder[R, R] { self: R => }
