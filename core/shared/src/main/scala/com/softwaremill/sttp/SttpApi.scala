package com.softwaremill.sttp

import java.io.InputStream
import java.nio.ByteBuffer

import com.softwaremill.sttp.internal._

import scala.collection.immutable.Seq
import scala.concurrent.duration._

trait SttpApi extends SttpExtensions {

  val DefaultReadTimeout: Duration = 1.minute

  /**
    * An empty request with no headers.
    */
  val emptyRequest: RequestT[Empty, String, Nothing] =
    RequestT[Empty, String, Nothing](
      None,
      None,
      NoBody,
      Vector(),
      asString,
      RequestOptions(followRedirects = true,
                     DefaultReadTimeout,
                     FollowRedirectsBackend.MaxRedirects,
                     StatusCodes.isSuccess),
      Map()
    )

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
    Multipart(name, FileBody(file), fileName = Some(file.name), contentType = Some(MediaTypes.Binary))

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

  // uri interpolator

  implicit class UriContext(val sc: StringContext) {
    def uri(args: Any*): Uri = UriInterpolator.interpolate(sc, args: _*)
  }
}
