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

  def ignore: ResponseAs[Unit, Nothing] = IgnoreResponse(identity)

  /**
    * Uses `utf-8` encoding.
    */
  def asString: ResponseAs[String, Nothing] = asString(Utf8)
  def asString(encoding: String): ResponseAs[String, Nothing] =
    ResponseAsString(encoding, identity)
  def asByteArray: ResponseAs[Array[Byte], Nothing] =
    ResponseAsByteArray(identity)

  /**
    * Uses `utf-8` encoding.
    */
  def asParams: ResponseAs[Seq[(String, String)], Nothing] =
    asParams(Utf8)
  def asParams(encoding: String): ResponseAs[Seq[(String, String)], Nothing] =
    ResponseAsParams(encoding, identity)

  def asStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S, S](identity)

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
  case class RequestT[U[_], T, +S](
      method: U[Method],
      uri: U[URI],
      body: RequestBody[S],
      headers: Seq[(String, String)],
      responseAs: ResponseAs[T, S]
  ) {
    def get(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.GET)
    def head(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.HEAD)
    def post(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.POST)
    def put(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.PUT)
    def delete(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.DELETE)
    def options(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.OPTIONS)
    def patch(uri: URI): Request[T, S] =
      this.copy[Id, T, S](uri = uri, method = Method.PATCH)

    def contentType(ct: String): RequestT[U, T, S] =
      header(ContentTypeHeader, ct, replaceExisting = true)
    def contentType(ct: String, encoding: String): RequestT[U, T, S] =
      header(ContentTypeHeader,
             contentTypeWithEncoding(ct, encoding),
             replaceExisting = true)
    def header(k: String,
               v: String,
               replaceExisting: Boolean = false): RequestT[U, T, S] = {
      val current =
        if (replaceExisting)
          headers.filterNot(_._1.equalsIgnoreCase(k))
        else headers
      this.copy(headers = current :+ (k -> v))
    }
    def headers(hs: Map[String, String]): RequestT[U, T, S] =
      this.copy(headers = headers ++ hs.toSeq)
    def headers(hs: (String, String)*): RequestT[U, T, S] =
      this.copy(headers = headers ++ hs)
    def cookie(nv: (String, String)): RequestT[U, T, S] = cookies(nv)
    def cookie(n: String, v: String): RequestT[U, T, S] = cookies((n, v))
    def cookies(r: Response[_]): RequestT[U, T, S] =
      cookies(r.cookies.map(c => (c.name, c.value)): _*)
    def cookies(cs: Seq[Cookie]): RequestT[U, T, S] =
      cookies(cs.map(c => (c.name, c.value)): _*)
    def cookies(nvs: (String, String)*): RequestT[U, T, S] =
      header(CookieHeader, nvs.map(p => p._1 + "=" + p._2).mkString("; "))
    def auth: SpecifyAuthScheme[U, T, S] =
      new SpecifyAuthScheme[U, T, S](AuthorizationHeader, this)
    def proxyAuth: SpecifyAuthScheme[U, T, S] =
      new SpecifyAuthScheme[U, T, S](ProxyAuthorizationHeader, this)
    def acceptEncoding(encoding: String): RequestT[U, T, S] =
      header(AcceptEncodingHeader, encoding)

    /**
      * Uses the `utf-8` encoding.
      * If content type is not yet specified, will be set to `text/plain`
      * with `utf-8` encoding.
      */
    def body(b: String): RequestT[U, T, S] = body(b, Utf8)

    /**
      * If content type is not yet specified, will be set to `text/plain`
      * with the given encoding.
      */
    def body(b: String, encoding: String): RequestT[U, T, S] =
      setContentTypeIfMissing(
        contentTypeWithEncoding(TextPlainContentType, encoding))
        .copy(body = StringBody(b, encoding))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: Array[Byte]): RequestT[U, T, S] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = ByteArrayBody(b))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: ByteBuffer): RequestT[U, T, S] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = ByteBufferBody(b))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: InputStream): RequestT[U, T, S] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = InputStreamBody(b))

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: File): RequestT[U, T, S] = body(b.toPath)

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body(b: Path): RequestT[U, T, S] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = PathBody(b))

    /**
      * Encodes the given parameters as form data using `utf-8`.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: Map[String, String]): RequestT[U, T, S] =
      formDataBody(fs.toList, Utf8)

    /**
      * Encodes the given parameters as form data.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: Map[String, String], encoding: String): RequestT[U, T, S] =
      formDataBody(fs.toList, encoding)

    /**
      * Encodes the given parameters as form data using `utf-8`.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: (String, String)*): RequestT[U, T, S] =
      formDataBody(fs.toList, Utf8)

    /**
      * Encodes the given parameters as form data.
      * If content type is not yet specified, will be set to
      * `application/x-www-form-urlencoded`.
      */
    def body(fs: Seq[(String, String)], encoding: String): RequestT[U, T, S] =
      formDataBody(fs, encoding)

    /**
      * If content type is not yet specified, will be set to
      * `application/octet-stream`.
      */
    def body[B: BodySerializer](b: B): RequestT[U, T, S] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(
        body = SerializableBody(implicitly[BodySerializer[B]], b))

    //def multipartData(parts: MultiPart*): RequestTemplate[U] = ???

    def streamBody[S2 >: S](b: S2): RequestT[U, T, S2] =
      this.copy[U, T, S2](body = StreamBody(b))

    /**
      * What's the target type to which the response body should be read.
      * Needs to be specified upfront so that the response is always consumed
      * and hence there are no requirements on client code to consume it. An
      * exception to this are streaming responses, which need to fully
      * consumed by the client if such a response type is requested.
      */
    def response[T2, S2 >: S](ra: ResponseAs[T2, S2]): RequestT[U, T2, S2] =
      this.copy(responseAs = ra)

    def mapResponse[T2](f: T => T2): RequestT[U, T2, S] =
      this.copy(responseAs = responseAs.map(f))

    def send[R[_]]()(implicit handler: SttpHandler[R, S],
                     isIdInRequest: IsIdInRequest[U]): R[Response[T]] = {
      // we could avoid the asInstanceOf by creating an artificial copy
      // changing the method & url fields using `isIdInRequest`, but that
      // would be only to satisfy the type checker, and a needless copy at
      // runtime.
      handler.send(this.asInstanceOf[RequestT[Id, T, S]])
    }

    private def hasContentType: Boolean =
      headers.exists(_._1.toLowerCase.contains(ContentTypeHeader))
    private def setContentTypeIfMissing(ct: String): RequestT[U, T, S] =
      if (hasContentType) this else contentType(ct)

    private def formDataBody(fs: Seq[(String, String)],
                             encoding: String): RequestT[U, T, S] = {
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

  class SpecifyAuthScheme[U[_], T, +S](hn: String, rt: RequestT[U, T, S]) {
    def basic(user: String, password: String): RequestT[U, T, S] = {
      val c = new String(
        Base64.getEncoder.encode(s"$user:$password".getBytes(Utf8)),
        Utf8)
      rt.header(hn, s"Basic $c")
    }

    def bearer(token: String): RequestT[U, T, S] =
      rt.header(hn, s"Bearer $token")
  }

  type PartialRequest[T, +S] = RequestT[Empty, T, S]
  type Request[T, +S] = RequestT[Id, T, S]

  @implicitNotFound(
    "This is a partial request, the method & url are not specified. Use " +
      ".get(...), .post(...) etc. to obtain a non-partial request.")
  private type IsIdInRequest[U[_]] = U[Unit] =:= Id[Unit]

  private[sttp] val ContentTypeHeader = "Content-Type"
  private[sttp] val ContentLengthHeader = "Content-Length"
  private[sttp] val SetCookieHeader = "Set-Cookie"
  private[sttp] val CookieHeader = "Cookie"
  private[sttp] val AuthorizationHeader = "Authorization"
  private[sttp] val ProxyAuthorizationHeader = "Proxy-Authorization"
  private[sttp] val AcceptEncodingHeader = "Accept-Encoding"
  private[sttp] val ContentEncodingHeader = "Content-Encoding"
  private val Utf8 = "utf-8"

  private val ApplicationOctetStreamContentType = "application/octet-stream"
  private val ApplicationFormContentType = "application/x-www-form-urlencoded"
  private val TextPlainContentType = "text/plain"
  private val MultipartFormDataContentType = "multipart/form-data"

  /**
    * An empty request with no headers.
    */
  val emptyRequest: RequestT[Empty, String, Nothing] =
    RequestT[Empty, String, Nothing](None, None, NoBody, Vector(), asString)

  /**
    * A starting request, with the following modifications comparing to
    * `emptyRequest`:
    *
    * - `Accept-Encoding` set to `gzip, deflate` (handled automatically by the
    *   library)
    */
  val sttp: RequestT[Empty, String, Nothing] =
    emptyRequest.acceptEncoding("gzip, deflate")

  private def contentTypeWithEncoding(ct: String, enc: String) =
    s"$ct; charset=$enc"

  implicit class UriContext(val sc: StringContext) extends AnyVal {
    def uri(args: Any*): URI = UriInterpolator.interpolate(sc, args: _*)
  }
}
