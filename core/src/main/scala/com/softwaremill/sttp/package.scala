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

  def ignoreResponse: ResponseAsBasic[Unit, Nothing] = IgnoreResponse
  /**
    * Uses `utf-8` encoding.
    */
  def responseAsString: ResponseAsBasic[String, Nothing] = responseAsString(Utf8)
  def responseAsString(encoding: String): ResponseAsBasic[String, Nothing] = ResponseAsString(encoding)
  def responseAsByteArray: ResponseAsBasic[Array[Byte], Nothing] = ResponseAsByteArray
  def responseAsStream[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

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
      * If content type is not yet specified, will be set to `text/plain` with `utf-8` encoding.
      */
    def data(b: String): RequestTemplate[U] = data(b, Utf8)
    /**
      * If content type is not yet specified, will be set to `text/plain` with the given encoding.
      */
    def data(b: String, encoding: String): RequestTemplate[U] =
      setContentTypeIfMissing(contentTypeWithEncoding(TextPlainContentType, encoding)).copy(body = StringBody(b, encoding))
    /**
      * If content type is not yet specified, will be set to `application/octet-stream`.
      */
    def data(b: Array[Byte]): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = ByteArrayBody(b))
    /**
      * If content type is not yet specified, will be set to `application/octet-stream`.
      */
    def data(b: ByteBuffer): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = ByteBufferBody(b))
    /**
      * If content type is not yet specified, will be set to `application/octet-stream`.
      */
    def data(b: InputStream): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = InputStreamBody(b))
    /**
      * If content type is not yet specified, will be set to `application/octet-stream`.
      */
    def data(b: File): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = FileBody(b))
    /**
      * If content type is not yet specified, will be set to `application/octet-stream`.
      */
    def data(b: Path): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = PathBody(b))
    /**
      * If content type is not yet specified, will be set to `application/octet-stream`.
      */
    def data[T: BodySerializer](b: T): RequestTemplate[U] =
      setContentTypeIfMissing(ApplicationOctetStreamContentType).copy(body = SerializableBody(implicitly[BodySerializer[T]], b))

    private def hasContentType: Boolean = headers.exists(_._1.toLowerCase.contains(ContentTypeHeader))
    private def setContentTypeIfMissing(ct: String): RequestTemplate[U] = if (hasContentType) this else contentType(ct)

    //def formData(fs: Map[String, Seq[String]]): RequestTemplate[U] = ???
    def formData(fs: Map[String, String]): RequestTemplate[U] = ???
    def formData(fs: (String, String)*): RequestTemplate[U] = ???

    def multipartData(parts: MultiPart*): RequestTemplate[U] = ???

    /**
      * @param responseAs What's the target type to which the response should be read. Needs to be specified upfront
      *                   so that the response is always consumed and hence there are no requirements on client code
      *                   to consume it. An exception to this are streaming responses, which need to fully consumed
      *                   by the client if such a response type is requested.
      */
    def send[R[_], S, T, TypeOfResponseAs[x, +s] <: ResponseAs[x, s]](responseAs: TypeOfResponseAs[T, S])(
      implicit handler: SttpHandler[R, S, TypeOfResponseAs], isRequest: IsRequest[U]): R[Response[T]] = {
      
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
