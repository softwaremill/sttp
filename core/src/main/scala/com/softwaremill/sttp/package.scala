package com.softwaremill

import java.io.{File, InputStream}
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Path

import com.softwaremill.sttp.model._

import scala.annotation.implicitNotFound
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
  private[sttp] val Utf8 = "utf-8"

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
    ResponseAsParams(encoding)

  def asStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

  // multi part factory methods

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

  // util

  private[sttp] def contentTypeWithEncoding(ct: String, enc: String) =
    s"$ct; charset=$enc"

  // uri interpolator

  implicit class UriContext(val sc: StringContext) extends AnyVal {
    def uri(args: Any*): URI = UriInterpolator.interpolate(sc, args: _*)
  }
}
