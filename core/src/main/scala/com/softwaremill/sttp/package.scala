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
  /*

  - set headers
  - set cookies (set from response)
  - partial request (no uri + method) / full request
  - start with an empty partial request
  - multi-part uploads
  - body: bytes, input stream (?), task/future, stream (fs2/akka), form data, file
  - auth
  - access uri/method/headers/cookies/body spec
  - proxy
  - user agent, buffer size
  - charset
  - zipped encodings
  - SSL - mutual? (client side)

  - stream responses (sendStreamAndReceive?) / strict responses
  - make sure response is consumed - only fire request when we know what to do with response?

  - reuse connections / connection pooling - in handler

  - handler restriction? AnyHandler <: Handler Restriction

  Options:
  - timeouts (connection/read)
  - follow redirect
  - ignore SSL

  //

  We want to serialize to:
  - string
  - byte array
  - input stream
  - handler-specific stream of bytes/strings

  post:
  - data (bytes/is/string - but which encoding?)
  - form data (kv pairs - application/x-www-form-urlencoded)
  - multipart (files mixed with forms - multipart/form-data)

   */

  //

  type Id[X] = X
  type Empty[X] = None.type

  def ignoreResponse: ResponseAs[Unit] = IgnoreResponse
  def responseAsString(encoding: String): ResponseAs[String] = ResponseAsString(encoding)
  def responseAsByteArray: ResponseAs[Array[Byte]] = ResponseAsByteArray
  def responseAsStream[S]: ResponseAsStream[S] = ResponseAsStream[S]()

  /**
    * Use the factory methods `multiPart` to conveniently create instances of this class. A part can be then
    * further customised using `fileName`, `contentType` and `header` methods.
    */
  case class MultiPart(name: String, data: BasicRequestBody, fileName: Option[String] = None,
    contentType: Option[String] = None, additionalHeaders: Map[String, String] = Map()) {
    def fileName(v: String): MultiPart = copy(fileName = Some(v))
    def contentType(v: String): MultiPart = copy(contentType = Some(v))
    def header(k: String, v: String): MultiPart = copy(additionalHeaders = additionalHeaders + (k -> v))
  }

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: String): MultiPart =
    MultiPart(name, StringBody(data, Utf8), contentType = Some(contentTypeWithEncoding(TextPlainContentType, Utf8)))
  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: String, encoding: String): MultiPart =
    MultiPart(name, StringBody(data, encoding), contentType = Some(contentTypeWithEncoding(TextPlainContentType, Utf8)))
  /**
    * Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: Array[Byte]): MultiPart =
    MultiPart(name, ByteArrayBody(data), contentType = Some(ApplicationOctetStreamContentType))
  /**
    * Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: ByteBuffer): MultiPart =
    MultiPart(name, ByteBufferBody(data), contentType = Some(ApplicationOctetStreamContentType))
  /**
    * Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: InputStream): MultiPart =
    MultiPart(name, InputStreamBody(data), contentType = Some(ApplicationOctetStreamContentType))
  /**
    * Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: File): MultiPart =
    MultiPart(name, FileBody(data), fileName = Some(data.getName), contentType = Some(ApplicationOctetStreamContentType))
  /**
    * Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    */
  def multiPart(name: String, data: Path): MultiPart =
    MultiPart(name, PathBody(data), fileName = Some(data.getFileName.toString), contentType = Some(ApplicationOctetStreamContentType))

  case class RequestTemplate[U[_]](
    method: U[Method],
    uri: U[URI],
    body: RequestBody,
    headers: Seq[(String, String)]
  ) {
    def get(uri: URI): Request = this.copy[Id](uri = uri, method = Method.GET)
    def head(uri: URI): Request = this.copy[Id](uri = uri, method = Method.HEAD)
    def post(uri: URI): Request = this.copy[Id](uri = uri, method = Method.POST)
    def put(uri: URI): Request = this.copy[Id](uri = uri, method = Method.PUT)
    def delete(uri: URI): Request = this.copy[Id](uri = uri, method = Method.DELETE)
    def options(uri: URI): Request = this.copy[Id](uri = uri, method = Method.OPTIONS)
    def patch(uri: URI): Request = this.copy[Id](uri = uri, method = Method.PATCH)

    def contentType(ct: String): RequestTemplate[U] =
      header(ContentTypeHeader, ct, replaceExisting = true)
    def contentType(ct: String, encoding: String): RequestTemplate[U] =
      header(ContentTypeHeader, contentTypeWithEncoding(ct, encoding), replaceExisting = true)
    def header(k: String, v: String, replaceExisting: Boolean = false): RequestTemplate[U] = {
      val kLower = k.toLowerCase
      val current = if (replaceExisting) headers.filterNot(_._1.toLowerCase.contains(kLower)) else headers
      this.copy(headers = current :+ (k -> v))
    }

    /**
      * If content type is not specified, will be set to `text/plain` with `utf-8` encoding.
      */
    def data(b: String): RequestTemplate[U] = data(b, Utf8)
    /**
      * If content type is not specified, will be set to `text/plain` with the given encoding.
      */
    def data(b: String, encoding: String): RequestTemplate[U] =
      setContentTypeIfMissing(contentTypeWithEncoding(TextPlainContentType, encoding)).copy(body = StringBody(b, encoding))
    /**
      * If content type is not specified, will be set to `application/octet-stream`.
      */
    def data(b: Array[Byte]): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = ByteArrayBody(b))
    /**
      * If content type is not specified, will be set to `application/octet-stream`.
      */
    def data(b: ByteBuffer): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = ByteBufferBody(b))
    /**
      * If content type is not specified, will be set to `application/octet-stream`.
      */
    def data(b: InputStream): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = InputStreamBody(b))
    /**
      * If content type is not specified, will be set to `application/octet-stream`.
      */
    def data(b: File): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = FileBody(b))
    /**
      * If content type is not specified, will be set to `application/octet-stream`.
      */
    def data(b: Path): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = PathBody(b))
    /**
      * If content type is not specified, will be set to `application/octet-stream`.
      */
    def data[T: BodySerializer](b: T): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = SerializableBody(implicitly[BodySerializer[T]], b))

    private def hasContentType: Boolean = headers.exists(_._1.toLowerCase.contains(ContentTypeHeader))
    private def setContentTypeIfMissing(ct: String): RequestTemplate[U] = if (hasContentType) this else contentType(ct)

    //def formData(fs: Map[String, Seq[String]]): RequestTemplate[U] = ???
    def formData(fs: Map[String, String]): RequestTemplate[U] = ???
    def formData(fs: (String, String)*): RequestTemplate[U] = ???

    def multipartData(parts: MultiPart*): RequestTemplate[U] = ???

    def send[R[_], T](responseAs: ResponseAs[T])(
      implicit handler: SttpHandler[R], isRequest: IsRequest[U]): R[Response[T]] = {

      handler.send(this, responseAs)
    }

    def send[R[_], S](responseAs: ResponseAsStream[S])(
      implicit handler: SttpStreamHandler[R, S], isRequest: IsRequest[U]): R[Response[S]] = {

      handler.send(this, responseAs)
    }
  }

  object RequestTemplate {
    val empty: RequestTemplate[Empty] = RequestTemplate[Empty](None, None, NoBody, Vector())
  }

  type PartialRequest = RequestTemplate[Empty]
  type Request = RequestTemplate[Id]

  @implicitNotFound("This is a partial request, the method & url are not specified. Use .get(...), .post(...) etc. to obtain a non-partial request.")
  private type IsRequest[U[_]] = RequestTemplate[U] =:= Request

  val sttp: RequestTemplate[Empty] = RequestTemplate.empty

  private val ContentTypeHeader = "content-type"
  private val Utf8 = "utf-8"

  private val ApplicationOctetStreamContentType = "application/octet-stream"
  private val ApplicationFormContentType = "application/x-www-form-urlencoded"
  private val TextPlainContentType = "text/plain"
  private val MultipartFormDataContentType = "multipart/form-data"

  private def contentTypeWithEncoding(ct: String, enc: String) = s"$ct; charset=$enc"
}
