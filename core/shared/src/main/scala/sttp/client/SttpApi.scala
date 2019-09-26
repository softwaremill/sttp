package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.client.internal._
import sttp.model._
import sttp.client.internal.SttpFile

import scala.collection.immutable.Seq
import scala.concurrent.duration._

trait SttpApi extends SttpExtensions with UriInterpolator {

  val DefaultReadTimeout: Duration = 1.minute

  /**
    * An empty request with no headers.
    */
  val emptyRequest: RequestT[Empty, Either[String, String], Nothing] =
    RequestT[Empty, Either[String, String], Nothing](
      None,
      None,
      NoBody,
      Vector(),
      asString,
      RequestOptions(
        followRedirects = true,
        DefaultReadTimeout,
        FollowRedirectsBackend.MaxRedirects,
        redirectToGet = false
      ),
      Map()
    )

  /**
    * A starting request, with the following modifications comparing to
    * `emptyRequest`:
    *
    * - `Accept-Encoding` set to `gzip, deflate` (handled automatically by the
    *   library)
    */
  val basicRequest: RequestT[Empty, Either[String, String], Nothing] =
    emptyRequest.acceptEncoding("gzip, deflate")

  // response specifications

  def ignore: ResponseAs[Unit, Nothing] = IgnoreResponse

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asString: ResponseAs[Either[String, String], Nothing] = asString(Utf8)

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asStringAlways: ResponseAs[String, Nothing] = asStringAlways(Utf8)

  /**
    * Use the given charset by default, unless specified otherwise in the response headers.
    */
  def asString(charset: String): ResponseAs[Either[String, String], Nothing] = asStringAlways(charset).mapWithMetadata {
    (s, m) =>
      if (m.isSuccess) Right(s) else Left(s)
  }

  def asStringAlways(charset: String): ResponseAs[String, Nothing] = asByteArrayAlways.mapWithMetadata {
    (bytes, metadata) =>
      val charset2 = metadata.contentType.flatMap(charsetFromContentType).getOrElse(charset)
      new String(bytes, charset2)
  }

  def asByteArray: ResponseAs[Either[String, Array[Byte]], Nothing] = asEither(asStringAlways, asByteArrayAlways)

  def asByteArrayAlways: ResponseAs[Array[Byte], Nothing] = ResponseAsByteArray

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asParams: ResponseAs[Either[String, Seq[(String, String)]], Nothing] =
    asParams(Utf8)

  /**
    * Use the given charset by default, unless specified otherwise in the response headers.
    */
  def asParams(charset: String): ResponseAs[Either[String, Seq[(String, String)]], Nothing] =
    asString(charset).mapRight(ResponseAs.parseParams(_, charset))

  def asStream[S]: ResponseAs[Either[String, S], S] = asEither(asStringAlways, asStreamAlways)

  def asStreamAlways[S]: ResponseAs[S, S] = ResponseAsStream[S, S]()

  private[client] def asSttpFile(file: SttpFile): ResponseAs[SttpFile, Nothing] =
    ResponseAsFile(file)

  def fromMetadata[T, S](f: ResponseMetadata => ResponseAs[T, S]): ResponseAs[T, S] = ResponseAsFromMetadata(f)

  def asEither[L, R, S](onError: ResponseAs[L, S], onSuccess: ResponseAs[R, S]): ResponseAs[Either[L, R], S] =
    fromMetadata { meta =>
      if (meta.isSuccess) onSuccess.map(Right(_)) else onError.map(Left(_))
    }

  // multipart factory methods

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String): Part[BasicRequestBody] =
    Part(name, StringBody(data, Utf8), contentType = Some(contentTypeWithCharset(MediaTypes.Text, Utf8)))

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String, encoding: String): Part[BasicRequestBody] =
    Part(name, StringBody(data, encoding), contentType = Some(contentTypeWithCharset(MediaTypes.Text, Utf8)))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: Array[Byte]): Part[BasicRequestBody] =
    Part(name, ByteArrayBody(data), contentType = Some(MediaTypes.Binary))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: ByteBuffer): Part[BasicRequestBody] =
    Part(name, ByteBufferBody(data), contentType = Some(MediaTypes.Binary))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: InputStream): Part[BasicRequestBody] =
    Part(name, InputStreamBody(data), contentType = Some(MediaTypes.Binary))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  private[client] def multipartSttpFile(name: String, file: SttpFile): Part[BasicRequestBody] =
    Part(name, FileBody(file), fileName = Some(file.name), contentType = Some(MediaTypes.Binary))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String]): Part[BasicRequestBody] =
    Part(name, RequestBody.paramsToStringBody(fs.toList, Utf8), contentType = Some(MediaTypes.Form))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String], encoding: String): Part[BasicRequestBody] =
    Part(name, RequestBody.paramsToStringBody(fs.toList, encoding), contentType = Some(MediaTypes.Form))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)]): Part[BasicRequestBody] =
    Part(name, RequestBody.paramsToStringBody(fs, Utf8), contentType = Some(MediaTypes.Form))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)], encoding: String): Part[BasicRequestBody] =
    Part(name, RequestBody.paramsToStringBody(fs, encoding), contentType = Some(MediaTypes.Form))

  /**
    * Content type will be set to `application/octet-stream`, can be
    * overridden later using the `contentType` method.
    */
  def multipart[B: BodySerializer](name: String, b: B): Part[BasicRequestBody] =
    Part(name, implicitly[BodySerializer[B]].apply(b), contentType = Some(MediaTypes.Binary))
}
