package com.softwaremill.sttp

import java.io.{ByteArrayOutputStream, InputStream, OutputStream }
import java.nio.ByteBuffer

import com.softwaremill.sttp.internal.SttpFile

import scala.annotation.{implicitNotFound, tailrec}
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.language.higherKinds

trait SttpApi extends sttpExtensions {
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
    * Backends might also provide special logic for serializer instances which they define (e.g. to handle streaming).
    */
  type BodySerializer[B] = B => BasicRequestBody

  val DefaultReadTimeout: Duration = 1.minute

  // constants

  private[sttp] val Utf8 = "utf-8"
  private[sttp] val Iso88591 = "iso-8859-1"
  private[sttp] val CrLf = "\r\n"

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
                                     RequestOptions(followRedirects = true, readTimeout = DefaultReadTimeout),
                                     Map())

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
    * Use the `utf-8` encoding by default, unless specified otherwise in the response headers.
    */
  def asString: ResponseAs[String, Nothing] = asString(Utf8)

  /**
    * Use the given encoding by default, unless specified otherwise in the response headers.
    */
  def asString(encoding: String): ResponseAs[String, Nothing] =
    ResponseAsString(encoding)

  def asByteArray: ResponseAs[Array[Byte], Nothing] =
    ResponseAsByteArray

  /**
    * Use the `utf-8` encoding by default, unless specified otherwise in the response headers.
    */
  def asParams: ResponseAs[Seq[(String, String)], Nothing] =
    asParams(Utf8)

  /**
    * Use the given encoding by default, unless specified otherwise in the response headers.
    */
  def asParams(encoding: String): ResponseAs[Seq[(String, String)], Nothing] =
    asString(encoding).map(ResponseAs.parseParams(_, encoding))

  def asStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

  private[sttp] def asSttpFile(file: SttpFile, overwrite: Boolean = false): ResponseAs[SttpFile, Nothing] =
    ResponseAsFile(file, overwrite)

  // multipart factory methods

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String): Multipart =
    Multipart(name, StringBody(data, Utf8), contentType = Some(contentTypeWithEncoding(MediaTypes.Text, Utf8)))

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String, encoding: String): Multipart =
    Multipart(name, StringBody(data, encoding), contentType = Some(contentTypeWithEncoding(MediaTypes.Text, Utf8)))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: Array[Byte]): Multipart =
    Multipart(name, ByteArrayBody(data), contentType = Some(MediaTypes.Binary))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: ByteBuffer): Multipart =
    Multipart(name, ByteBufferBody(data), contentType = Some(MediaTypes.Binary))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: InputStream): Multipart =
    Multipart(name, InputStreamBody(data), contentType = Some(MediaTypes.Binary))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  private[sttp] def multipartSttpFile(name: String, file: SttpFile): Multipart =
    Multipart(name,
              FileBody(file),
              fileName = Some(file.name),
              contentType = Some(MediaTypes.Binary))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String]): Multipart =
    Multipart(name, RequestBody.paramsToStringBody(fs.toList, Utf8), contentType = Some(MediaTypes.Form))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String], encoding: String): Multipart =
    Multipart(name, RequestBody.paramsToStringBody(fs.toList, encoding), contentType = Some(MediaTypes.Form))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)]): Multipart =
    Multipart(name, RequestBody.paramsToStringBody(fs, Utf8), contentType = Some(MediaTypes.Form))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)], encoding: String): Multipart =
    Multipart(name, RequestBody.paramsToStringBody(fs, encoding), contentType = Some(MediaTypes.Form))

  /**
    * Content type will be set to `application/octet-stream`, can be
    * overridden later using the `contentType` method.
    */
  def multipart[B: BodySerializer](name: String, b: B): Multipart =
    Multipart(name, implicitly[BodySerializer[B]].apply(b), contentType = Some(MediaTypes.Binary))

  // util

  private[sttp] def contentTypeWithEncoding(ct: String, enc: String): String =
    s"$ct; charset=$enc"

  private[sttp] def encodingFromContentType(ct: String): Option[String] =
    ct.split(";").map(_.trim.toLowerCase).collectFirst {
      case s if s.startsWith("charset=") => s.substring(8)
    }

  private[sttp] def transfer(is: InputStream, os: OutputStream): Unit = {
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

  private[sttp] def toByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    transfer(is, os)
    os.toByteArray
  }

  private[sttp] def concatByteBuffers(bb1: ByteBuffer, bb2: ByteBuffer): ByteBuffer =
    ByteBuffer
      .allocate(bb1.array().length + bb2.array().length)
      .put(bb1)
      .put(bb2)

  // uri interpolator

  implicit class UriContext(val sc: StringContext) {
    def uri(args: Any*): Uri = UriInterpolator.interpolate(sc, args: _*)
  }
}
