package com.softwaremill

import java.io._
import java.nio.ByteBuffer
import java.nio.file.Path

import scala.annotation.{implicitNotFound, tailrec}
import scala.language.higherKinds
import scala.collection.immutable.Seq

package object sttp {
  type Id[X] = X
  type Empty[X] = None.type

  type PartialRequest[T, +S] = RequestT[Empty, T, S]
  type Request[T, +S] = RequestT[Id, T, S]

  @implicitNotFound(
    "This is a partial request, the method & url are not specified. Use " +
      ".get(...), .post(...) etc. to obtain a non-partial request.")
  private[sttp] type IsIdInRequest[U[_]] = U[Unit] =:= Id[Unit]

  /**
    * Provide an implicit value of this type to serialize arbitrary classes into a request body.
    * Handlers might also provide special logic for serializer instances which they define (e.g. to handle streaming).
    */
  type BodySerializer[B] = B => BasicRequestBody

  // constants

  private[sttp] val ContentTypeHeader = "Content-Type"
  private[sttp] val ContentLengthHeader = "Content-Length"
  private[sttp] val SetCookieHeader = "Set-Cookie"
  private[sttp] val CookieHeader = "Cookie"
  private[sttp] val AuthorizationHeader = "Authorization"
  private[sttp] val ProxyAuthorizationHeader = "Proxy-Authorization"
  private[sttp] val AcceptEncodingHeader = "Accept-Encoding"
  private[sttp] val ContentEncodingHeader = "Content-Encoding"
  private[sttp] val ContentDispositionHeader = "Content-Disposition"
  private[sttp] val LocationHeader = "Location"
  private[sttp] val Utf8 = "utf-8"
  private[sttp] val Iso88591 = "iso-8859-1"
  private[sttp] val CrLf = "\r\n"

  private[sttp] val ApplicationOctetStreamContentType =
    "application/octet-stream"
  private[sttp] val ApplicationFormContentType =
    "application/x-www-form-urlencoded"
  private[sttp] val TextPlainContentType = "text/plain"
  private[sttp] val MultipartFormDataContentType = "multipart/form-data"

  // entry points

  /**
    * An empty request with no headers.
    */
  val emptyRequest: RequestT[Empty, String, Nothing] =
    RequestT[Empty, String, Nothing](None,
                                     None,
                                     NoBody,
                                     Vector(),
                                     asString,
                                     RequestOptions(followRedirects = true))

  /**
    * A starting request, with the following modifications comparing to
    * `emptyRequest`:
    *
    * - `Accept-Encoding` set to `gzip, deflate` (handled automatically by the
    *   library)
    */
  val sttp: RequestT[Empty, String, Nothing] =
    emptyRequest.acceptEncoding("gzip, deflate")

  // response specifications

  def ignore: ResponseAs[Unit, Nothing] = IgnoreResponse

  /**
    * Uses `utf-8` encoding.
    */
  def asString: ResponseAs[String, Nothing] = asString(Utf8)
  def asString(encoding: String): ResponseAs[String, Nothing] =
    ResponseAsString(encoding)
  def asByteArray: ResponseAs[Array[Byte], Nothing] =
    ResponseAsByteArray

  /**
    * Uses `utf-8` encoding.
    */
  def asParams: ResponseAs[Seq[(String, String)], Nothing] =
    asParams(Utf8)
  def asParams(encoding: String): ResponseAs[Seq[(String, String)], Nothing] =
    asString(encoding).map(ResponseAs.parseParams(_, encoding))

  def asStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

  def asFile(file: File,
             overwrite: Boolean = false): ResponseAs[File, Nothing] =
    ResponseAsFile(file, overwrite)

  def asPath(path: Path,
             overwrite: Boolean = false): ResponseAs[Path, Nothing] =
    ResponseAsFile(path.toFile, overwrite).map(_.toPath)

  // multipart factory methods

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String): Multipart =
    Multipart(name,
              StringBody(data, Utf8),
              contentType =
                Some(contentTypeWithEncoding(TextPlainContentType, Utf8)))

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String, encoding: String): Multipart =
    Multipart(name,
              StringBody(data, encoding),
              contentType =
                Some(contentTypeWithEncoding(TextPlainContentType, Utf8)))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: Array[Byte]): Multipart =
    Multipart(name,
              ByteArrayBody(data),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: ByteBuffer): Multipart =
    Multipart(name,
              ByteBufferBody(data),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: InputStream): Multipart =
    Multipart(name,
              InputStreamBody(data),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipart(name: String, data: File): Multipart =
    multipart(name, data.toPath)

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  def multipart(name: String, data: Path): Multipart =
    Multipart(name,
              PathBody(data),
              fileName = Some(data.getFileName.toString),
              contentType = Some(ApplicationOctetStreamContentType))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String]): Multipart =
    Multipart(name,
              RequestBody.paramsToStringBody(fs.toList, Utf8),
              contentType = Some(ApplicationFormContentType))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String,
                fs: Map[String, String],
                encoding: String): Multipart =
    Multipart(name,
              RequestBody.paramsToStringBody(fs.toList, encoding),
              contentType = Some(ApplicationFormContentType))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)]): Multipart =
    Multipart(name,
              RequestBody.paramsToStringBody(fs, Utf8),
              contentType = Some(ApplicationFormContentType))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String,
                fs: Seq[(String, String)],
                encoding: String): Multipart =
    Multipart(name,
              RequestBody.paramsToStringBody(fs, encoding),
              contentType = Some(ApplicationFormContentType))

  /**
    * Content type will be set to `application/octet-stream`, can be
    * overridden later using the `contentType` method.
    */
  def multipart[B: BodySerializer](name: String, b: B): Multipart =
    Multipart(name,
              implicitly[BodySerializer[B]].apply(b),
              contentType = Some(ApplicationOctetStreamContentType))

  // util

  private[sttp] def contentTypeWithEncoding(ct: String, enc: String) =
    s"$ct; charset=$enc"

  private[sttp] def transfer(is: InputStream, os: OutputStream) {
    var read = 0
    val buf = new Array[Byte](1024)

    @tailrec
    def transfer(): Unit = {
      read = is.read(buf, 0, buf.length)
      if (read != -1) {
        os.write(buf, 0, read)
        transfer()
      }
    }

    transfer()
  }

  // uri interpolator

  implicit class UriContext(val sc: StringContext) extends AnyVal {
    def uri(args: Any*): Uri = UriInterpolator.interpolate(sc, args: _*)
  }
}
