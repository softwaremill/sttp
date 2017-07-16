package com.softwaremill

import java.io.{File, InputStream}
import java.net.{URI, URLEncoder}
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Base64

import com.softwaremill.sttp.model._

import scala.annotation.implicitNotFound
import scala.language.higherKinds
import scala.collection.immutable.Seq

package object sttp {
  type Id[X] = X
  type Empty[X] = None.type

  def ignoreResponse: ResponseAs[Unit, Nothing] = IgnoreResponse

  /**
    * Uses `utf-8` encoding.
    */
  def responseAsString: ResponseAs[String, Nothing] = responseAsString(Utf8)
  def responseAsString(encoding: String): ResponseAs[String, Nothing] =
    ResponseAsString(encoding)
  def responseAsByteArray: ResponseAs[Array[Byte], Nothing] =
    ResponseAsByteArray

  /**
    * Uses `utf-8` encoding.
    */
  def responseAsParams: ResponseAs[Seq[(String, String)], Nothing] =
    responseAsParams(Utf8)
  def responseAsParams(
      encoding: String): ResponseAs[Seq[(String, String)], Nothing] =
    ResponseAsParams(encoding)

  def responseAsStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

  /**
    * Use the factory methods `multiPart` to conveniently create instances of
    * this class. A part can be then further customised using `fileName`,
    * `contentType` and `header` methods.
    */
  case class MultiPart(name: String,
                       data: BasicRequestBody,
                       fileName: Option[String] = None,
                       contentType: Option[String] = None,
                       additionalHeaders: Map[String, String] = Map()) {
    def fileName(v: String): MultiPart = copy(fileName = Some(v))
    def contentType(v: String): MultiPart = copy(contentType = Some(v))
    def header(k: String, v: String): MultiPart =
      copy(additionalHeaders = additionalHeaders + (k -> v))
  }

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: String): MultiPart =
    MultiPart(name,
              StringBody(data, Utf8),
              contentType =
                Some(contentTypeWithEncoding(TextPlainContentType, Utf8)))

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: String, encoding: String): MultiPart =
    MultiPart(name,
              StringBody(data, encoding),
              contentType =
                Some(contentTypeWithEncoding(TextPlainContentType, Utf8)))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multiPart(name: String, data: Array[Byte]): MultiPart =
    MultiPart(name,
              ByteArrayBody(data),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multiPart(name: String, data: ByteBuffer): MultiPart =
    MultiPart(name,
              ByteBufferBody(data),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multiPart(name: String, data: InputStream): MultiPart =
    MultiPart(name,
              InputStreamBody(data),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multiPart(name: String, data: File): MultiPart =
    multiPart(name, data.toPath)

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multiPart(name: String, data: Path): MultiPart =
    MultiPart(name,
              PathBody(data),
              fileName = Some(data.getFileName.toString),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * @tparam U Specifies if the method & uri are specified. By default can be
    *           either:
    *           * `Empty`, which is a type constructor which always resolves to
    *           `None`. This type of request is aliased to `PartialRequest`:
    *           there's no method and uri specified, and the request cannot be
    *           sent.
    *           * `Id`, which is an identity type constructor. This type of
    *           request is aliased to `Request`: the method and uri are
    *           specified, and the request can be sent.
    */
  case class RequestTemplate[U[_]](
      method: U[Method],
      uri: U[URI],
      body: RequestBody,
      headers: Seq[(String, String)]
  ) {
    def get(uri: URI): Request = this.copy[Id](uri = uri, method = Method.GET)
    def head(uri: URI): Request =
      this.copy[Id](uri = uri, method = Method.HEAD)
    def post(uri: URI): Request =
      this.copy[Id](uri = uri, method = Method.POST)
    def put(uri: URI): Request = this.copy[Id](uri = uri, method = Method.PUT)
    def delete(uri: URI): Request =
      this.copy[Id](uri = uri, method = Method.DELETE)
    def options(uri: URI): Request =
      this.copy[Id](uri = uri, method = Method.OPTIONS)
    def patch(uri: URI): Request =
      this.copy[Id](uri = uri, method = Method.PATCH)

    def contentType(ct: String): RequestTemplate[U] =
      header(ContentTypeHeader, ct, replaceExisting = true)
    def contentType(ct: String, encoding: String): RequestTemplate[U] =
      header(ContentTypeHeader,
             contentTypeWithEncoding(ct, encoding),
             replaceExisting = true)
    def header(k: String,
               v: String,
               replaceExisting: Boolean = false): RequestTemplate[U] = {
      val current =
        if (replaceExisting)
          headers.filterNot(_._1.equalsIgnoreCase(k))
        else headers
      this.copy(headers = current :+ (k -> v))
    }
    def headers(hs: Map[String, String]): RequestTemplate[U] =
      this.copy(headers = headers ++ hs.toSeq)
    def headers(hs: (String, String)*): RequestTemplate[U] =
      this.copy(headers = headers ++ hs)
    def cookie(nv: (String, String)): RequestTemplate[U] = cookies(nv)
    def cookie(n: String, v: String): RequestTemplate[U] = cookies((n, v))
    def cookies(r: Response[_]): RequestTemplate[U] =
      cookies(r.cookies.map(c => (c.name, c.value)): _*)
    def cookies(cs: Seq[Cookie]): RequestTemplate[U] =
      cookies(cs.map(c => (c.name, c.value)): _*)
    def cookies(nvs: (String, String)*): RequestTemplate[U] =
      header(CookieHeader, nvs.map(p => p._1 + "=" + p._2).mkString("; "))
    def auth: SpecifyAuthScheme[U] =
      new SpecifyAuthScheme[U](AuthorizationHeader, this)
    def proxyAuth: SpecifyAuthScheme[U] =
      new SpecifyAuthScheme[U](ProxyAuthorizationHeader, this)

    /**
      * Uses the `utf-8` encoding.
      * If content type is not yet specified, will be set to `text/plain`
      * with `utf-8` encoding.
      */
    def body(b: String): RequestTemplate[U] = body(b, Utf8)

    /**
      * If content type is not yet specified, will be set to `text/plain`
      * with the given encoding.
      */
    def body(b: String, encoding: String): RequestTemplate[U] =
      setContentTypeIfMissing(
        contentTypeWithEncoding(TextPlainContentType, encoding))
        .copy(body = StringBody(b, encoding))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: Array[Byte]): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = ByteArrayBody(b))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: ByteBuffer): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = ByteBufferBody(b))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: InputStream): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = InputStreamBody(b))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: File): RequestTemplate[U] = body(b.toPath)

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: Path): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = PathBody(b))

    /**
      * Encodes the given parameters as form data using `utf-8`.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: Map[String, String]): RequestTemplate[U] =
      formDataBody(fs.toList, Utf8)

    /**
      * Encodes the given parameters as form data.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: Map[String, String], encoding: String): RequestTemplate[U] =
      formDataBody(fs.toList, encoding)

    /**
      * Encodes the given parameters as form data using `utf-8`.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: (String, String)*): RequestTemplate[U] =
      formDataBody(fs.toList, Utf8)

    /**
      * Encodes the given parameters as form data.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: Seq[(String, String)], encoding: String): RequestTemplate[U] =
      formDataBody(fs, encoding)

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body[T: BodySerializer](b: T): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = SerializableBody(implicitly[BodySerializer[T]], b))

    //def multipartData(parts: MultiPart*): RequestTemplate[U] = ???

    /**
      * @param responseAs What's the target type to which the response body
      *                   should be read. Needs to be specified upfront
      *                   so that the response is always consumed and hence
      *                   there are no requirements on client code to consume
      *                   it. An exception to this are streaming responses,
      *                   which need to fully consumed by the client if such
      *                   a response type is requested.
      */
    def send[R[_], S, T](responseAs: ResponseAs[T, S])(
        implicit handler: SttpHandler[R, S],
        isRequest: IsRequest[U]): R[Response[T]] = {

      handler.send(this, responseAs)
    }

    private def hasContentType: Boolean =
      headers.exists(_._1.toLowerCase.contains(ContentTypeHeader))
    private def setContentTypeIfMissing(ct: String): RequestTemplate[U] =
      if (hasContentType) this else contentType(ct)

    private def formDataBody(fs: Seq[(String, String)],
                             encoding: String): RequestTemplate[U] = {
      val b = fs
        .map(
          p =>
            URLEncoder.encode(p._1, encoding) + "=" + URLEncoder
              .encode(p._2, encoding))
        .mkString("&")
      setContentTypeIfMissing(ApplicationFormContentType).copy(
        body = StringBody(b, encoding))
    }
  }

  class SpecifyAuthScheme[U[_]](hn: String, rt: RequestTemplate[U]) {
    def basic(user: String, password: String): RequestTemplate[U] = {
      val c = new String(
        Base64.getEncoder.encode(s"$user:$password".getBytes(Utf8)),
        Utf8)
      rt.header(hn, s"Basic $c")
    }

    def bearer(token: String): RequestTemplate[U] =
      rt.header(hn, s"Bearer $token")
  }

  object RequestTemplate {
    val empty: RequestTemplate[Empty] =
      RequestTemplate[Empty](None, None, NoBody, Vector())
  }

  type PartialRequest = RequestTemplate[Empty]
  type Request = RequestTemplate[Id]

  @implicitNotFound(
    "This is a partial request, the method & url are not specified. Use " +
      ".get(...), .post(...) etc. to obtain a non-partial request.")
  private type IsRequest[U[_]] = RequestTemplate[U] =:= Request

  val sttp: RequestTemplate[Empty] = RequestTemplate.empty

  private[sttp] val ContentTypeHeader = "Content-Type"
  private[sttp] val ContentLengthHeader = "Content-Length"
  private[sttp] val SetCookieHeader = "Set-Cookie"
  private[sttp] val CookieHeader = "Cookie"
  private[sttp] val AuthorizationHeader = "Authorization"
  private[sttp] val ProxyAuthorizationHeader = "Proxy-Authorization"
  private val Utf8 = "utf-8"

  private val ApplicationOctetStreamContentType = "application/octet-stream"
  private val ApplicationFormContentType = "application/x-www-form-urlencoded"
  private val TextPlainContentType = "text/plain"
  private val MultipartFormDataContentType = "multipart/form-data"

  private def contentTypeWithEncoding(ct: String, enc: String) =
    s"$ct; charset=$enc"

  implicit class UriContext(val sc: StringContext) extends AnyVal {
    def uri(args: Any*): URI = UriInterpolator.interpolate(sc, args: _*)
  }
}
