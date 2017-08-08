package com.softwaremill.sttp

import java.io.{File, InputStream}
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Base64

import com.softwaremill.sttp.model._

import scala.collection.immutable.Seq

import scala.language.higherKinds

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
    uri: U[Uri],
    body: RequestBody[S],
    headers: Seq[(String, String)],
    responseAs: ResponseAs[T, S]
) {
  def get(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.GET)
  def head(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.HEAD)
  def post(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.POST)
  def put(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.PUT)
  def delete(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.DELETE)
  def options(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.OPTIONS)
  def patch(uri: Uri): Request[T, S] =
    this.copy[Id, T, S](uri = uri, method = Method.PATCH)

  def contentType(ct: String): RequestT[U, T, S] =
    header(ContentTypeHeader, ct, replaceExisting = true)
  def contentType(ct: String, encoding: String): RequestT[U, T, S] =
    header(ContentTypeHeader,
           contentTypeWithEncoding(ct, encoding),
           replaceExisting = true)
  def contentLength(l: Long): RequestT[U, T, S] =
    header(ContentLengthHeader, l.toString, replaceExisting = true)
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
    headers(hs.toSeq: _*)
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
    setContentTypeIfMissing(
      contentTypeWithEncoding(TextPlainContentType, encoding))
      .setContentLengthIfMissing(b.getBytes(encoding).length)
      .copy(body = StringBody(b, encoding))

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given array.
    */
  def body(b: Array[Byte]): RequestT[U, T, S] =
    setContentTypeIfMissing(ApplicationOctetStreamContentType)
      .setContentLengthIfMissing(b.length)
      .copy(body = ByteArrayBody(b))

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
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(b: File): RequestT[U, T, S] =
    body(b.toPath)

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    *
    * If content length is not yet specified, will be set to the length
    * of the given file.
    */
  def body(b: Path): RequestT[U, T, S] =
    setContentTypeIfMissing(ApplicationOctetStreamContentType)
      .setContentLengthIfMissing(b.toFile.length())
      .copy(body = PathBody(b))

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

  /**
    * If content type is not yet specified, will be set to
    * `application/octet-stream`.
    */
  def body[B](b: B)(implicit serializer: BodySerializer[B]): RequestT[U, T, S] =
    setContentTypeIfMissing(
      serializer.defaultContentType.getOrElse(
        ApplicationOctetStreamContentType))
      .copy(body = SerializableBody(serializer, b))

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

  private[sttp] def hasContentType: Boolean =
    headers.exists(_._1.equalsIgnoreCase(ContentTypeHeader))
  private[sttp] def setContentTypeIfMissing(ct: String): RequestT[U, T, S] =
    if (hasContentType) this else contentType(ct)

  private def hasContentLength: Boolean =
    headers.exists(_._1.equalsIgnoreCase(ContentLengthHeader))
  private def setContentLengthIfMissing(l: => Long): RequestT[U, T, S] =
    if (hasContentLength) this else contentLength(l)

  private def formDataBody(fs: Seq[(String, String)],
                           encoding: String): RequestT[U, T, S] = {
    val b = fs
      .map(
        p =>
          URLEncoder.encode(p._1, encoding) + "=" + URLEncoder
            .encode(p._2, encoding))
      .mkString("&")
    setContentTypeIfMissing(ApplicationFormContentType)
      .setContentLengthIfMissing(b.getBytes(encoding).length)
      .copy(body = StringBody(b, encoding))
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
