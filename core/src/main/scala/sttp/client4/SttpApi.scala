package sttp.client4

import sttp.client4.internal._
import sttp.model._

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import sttp.capabilities.Streams
import sttp.capabilities.Effect
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.logging.LoggingOptions
import sttp.attributes.AttributeMap

trait SttpApi extends SttpExtensions with UriInterpolator {
  val DefaultReadTimeout: Duration = 1.minute

  /** An empty request with no headers.
    *
    * Reads the response body as an `Either[String, String]`, where `Left` is used if the status code is non-2xx, and
    * `Right` otherwise.
    */
  val emptyRequest: PartialRequest[Either[String, String]] =
    PartialRequest(
      NoBody,
      Vector(),
      asString,
      RequestOptions(
        followRedirects = true,
        DefaultReadTimeout,
        FollowRedirectsBackend.MaxRedirects,
        redirectToGet = false,
        disableAutoDecompression = false,
        httpVersion = None,
        loggingOptions = LoggingOptions()
      ),
      AttributeMap.Empty
    )

  /** A starting request, with the `Accept-Encoding` header set to `gzip, deflate` (compression/decompression is handled
    * automatically by the library).
    *
    * Reads the response body as an `Either[String, String]`, where `Left` is used if the status code is non-2xx, and
    * `Right` otherwise.
    *
    * @see
    *   [[emptyRequest]] for a starting request which has no headers set
    * @see
    *   [[quickRequest]] for a starting request which always reads the response body as a [[String]], without the
    *   [[Either]] wrapper
    */
  val basicRequest: PartialRequest[Either[String, String]] =
    emptyRequest.acceptEncoding("gzip, deflate")

  /** A starting request which always reads the response body as a string, regardless of the status code. */
  val quickRequest: PartialRequest[String] = basicRequest.response(asStringAlways)

  // response descriptions

  /** Ignores (discards) the response. */
  def ignore: ResponseAs[Unit] = ResponseAs(IgnoreResponse)

  /** Reads the response as an `Either[String, String]`, where `Left` is used if the status code is non-2xx, and `Right`
    * otherwise. Uses the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asString: ResponseAs[Either[String, String]] = asString(Utf8)

  /** Reads the response as a `String`, regardless of the status code. Use the `utf-8` charset by default, unless
    * specified otherwise in the response headers.
    */
  def asStringAlways: ResponseAs[String] = asStringAlways(Utf8)

  /** Reads the response as an `Either[String, String]`, where `Left` is used if the status code is non-2xx, and `Right`
    * otherwise. Uses the given charset by default, unless specified otherwise in the response headers.
    */
  def asString(charset: String): ResponseAs[Either[String, String]] =
    asStringAlways(charset)
      .mapWithMetadata { (s, m) =>
        if (m.isSuccess) Right(s) else Left(s)
      }
      .showAs("either(as string, as string)")

  /** Reads the response as a `String`, regardless of the status code. Uses the given charset by default, unless
    * specified otherwise in the response headers.
    */
  def asStringAlways(charset: String): ResponseAs[String] =
    asByteArrayAlways
      .mapWithMetadata { (bytes, metadata) =>
        val charset2 = metadata.contentType.flatMap(charsetFromContentType).getOrElse(charset)
        val charset3 = sanitizeCharset(charset2)
        new String(bytes, charset3)
      }
      .showAs("as string")

  /** Reads the response as either a string (for non-2xx responses), or othweise as an array of bytes (without any
    * processing). The entire response is loaded into memory.
    */
  def asByteArray: ResponseAs[Either[String, Array[Byte]]] = asEither(asStringAlways, asByteArrayAlways)

  /** Reads the response as an array of bytes, without any processing, regardless of the status code. The entire
    * response is loaded into memory.
    */
  def asByteArrayAlways: ResponseAs[Array[Byte]] = ResponseAs(ResponseAsByteArray)

  /** Deserializes the response as either a string (for non-2xx responses), or otherwise as form parameters. Uses the
    * `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asParams: ResponseAs[Either[String, Seq[(String, String)]]] = asParams(Utf8)

  /** Deserializes the response as form parameters, regardless of the status code. Uses the `utf-8` charset by default,
    * unless specified otherwise in the response headers.
    */
  def asParamsAlways: ResponseAs[Seq[(String, String)]] = asParamsAlways(Utf8)

  /** Deserializes the response as either a string (for non-2xx responses), or otherwise as form parameters. Uses the
    * given charset by default, unless specified otherwise in the response headers.
    */
  def asParams(charset: String): ResponseAs[Either[String, Seq[(String, String)]]] =
    asEither(asStringAlways, asParamsAlways(charset)).showAs("either(as string, as params)")

  /** Deserializes the response as form parameters, regardless of the status code. Uses the given charset by default,
    * unless specified otherwise in the response headers.
    */
  def asParamsAlways(charset: String): ResponseAs[Seq[(String, String)]] = {
    val charset2 = sanitizeCharset(charset)
    asStringAlways(charset2).map(GenericResponseAs.parseParams(_, charset2)).showAs("as params")
  }

  private[client4] def asSttpFile(file: SttpFile): ResponseAs[SttpFile] = ResponseAs(ResponseAsFile(file))

  /** Uses the [[ResponseAs]] description that matches the condition (using the response's metadata).
    *
    * This allows using different response description basing on the status code, for example. If none of the conditions
    * match, the default response handling description is used.
    */
  def fromMetadata[T](default: ResponseAs[T], conditions: ConditionalResponseAs[ResponseAs[T]]*): ResponseAs[T] =
    ResponseAs(ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate))

  /** Uses the `onSuccess` response description for successful responses (2xx), and the `onError` description otherwise.
    */
  def asEither[A, B](onError: ResponseAs[A], onSuccess: ResponseAs[B]): ResponseAs[Either[A, B]] =
    fromMetadata(onError.map(Left(_)), ConditionalResponseAs(_.isSuccess, onSuccess.map(Right(_))))
      .showAs(s"either(${onError.show}, ${onSuccess.show})")

  /** Uses both `l` and `r` to handle the response body. Neither response descriptions may use streaming or web sockets.
    */
  def asBoth[A, B](l: ResponseAs[A], r: ResponseAs[B]): ResponseAs[(A, B)] =
    asBothOption(l, r)
      .map { case (a, bo) =>
        // since l has no requirements, we know that the body will be replayable
        (a, bo.get)
      }
      .showAs(s"(${l.show}, ${r.show})")

  /** Uses `l` to handle the response body. If the raw body value which is used by `l` is replayable (a file or byte
    * array), also uses `r` to read the response body. Otherwise ignores `r` (if the raw body is a stream).
    */
  def asBothOption[A, B](l: ResponseAs[A], r: ResponseAs[B]): ResponseAs[(A, Option[B])] =
    ResponseAs(ResponseAsBoth(l.delegate, r.delegate))

  // multipart factory methods

  /** Content type will be set to `text/plain` with `utf-8` encoding, can be overridden later using the `contentType`
    * method.
    */
  def multipart(name: String, data: String): Part[BasicBodyPart] =
    Part(name, StringBody(data, Utf8), contentType = Some(MediaType.TextPlainUtf8))

  /** Content type will be set to `text/plain` with given encoding, can be overridden later using the `contentType`
    * method.
    */
  def multipart(name: String, data: String, encoding: String): Part[BasicBodyPart] =
    Part(name, StringBody(data, encoding), contentType = Some(MediaType.TextPlain.charset(encoding)))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method. */
  def multipart(name: String, data: Array[Byte]): Part[BasicBodyPart] =
    Part(name, ByteArrayBody(data), contentType = Some(MediaType.ApplicationOctetStream))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method. */
  def multipart(name: String, data: ByteBuffer): Part[BasicBodyPart] =
    Part(name, ByteBufferBody(data), contentType = Some(MediaType.ApplicationOctetStream))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method. */
  def multipart(name: String, data: InputStream): Part[BasicBodyPart] =
    Part(name, InputStreamBody(data), contentType = Some(MediaType.ApplicationOctetStream))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  private[client4] def multipartSttpFile(name: String, file: SttpFile): Part[BasicBodyPart] =
    Part(name, FileBody(file), fileName = Some(file.name), contentType = Some(MediaType.ApplicationOctetStream))

  /** Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be overridden later using the `contentType`
    * method.
    */
  def multipart(name: String, fs: Map[String, String]): Part[BasicBodyPart] =
    Part(
      name,
      BasicBody.paramsToStringBody(fs.toList, Utf8),
      contentType = Some(MediaType.ApplicationXWwwFormUrlencoded)
    )

  /** Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be overridden later using the `contentType`
    * method.
    */
  def multipart(name: String, fs: Map[String, String], encoding: String): Part[BasicBodyPart] =
    Part(
      name,
      BasicBody.paramsToStringBody(fs.toList, encoding),
      contentType = Some(MediaType.ApplicationXWwwFormUrlencoded)
    )

  /** Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be overridden later using the `contentType`
    * method.
    */
  def multipart(name: String, fs: Seq[(String, String)]): Part[BasicBodyPart] =
    Part(name, BasicBody.paramsToStringBody(fs, Utf8), contentType = Some(MediaType.ApplicationXWwwFormUrlencoded))

  /** Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be overridden later using the `contentType`
    * method.
    */
  def multipart(name: String, fs: Seq[(String, String)], encoding: String): Part[BasicBodyPart] =
    Part(
      name,
      BasicBody.paramsToStringBody(fs, encoding),
      contentType = Some(MediaType.ApplicationXWwwFormUrlencoded)
    )

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method. */
  def multipart(name: String, b: BasicBodyPart): Part[BasicBodyPart] =
    Part(name, b, contentType = Some(MediaType.ApplicationXWwwFormUrlencoded))

  // stream response specifications

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise providing a stream with
    * the response's data to `f`. The stream is always closed after `f` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asStream[F[_], T, S](s: Streams[S])(
      f: s.BinaryStream => F[T]
  ): StreamResponseAs[Either[String, T], S with Effect[F]] =
    asEither(asStringAlways, asStreamAlways(s)(f))

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise providing a stream with
    * the response's data, along with the response metadata, to `f`. The stream is always closed after `f` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asStreamWithMetadata[F[_], T, S](s: Streams[S])(
      f: (s.BinaryStream, ResponseMetadata) => F[T]
  ): StreamResponseAs[Either[String, T], S with Effect[F]] =
    asEither(asStringAlways, asStreamAlwaysWithMetadata(s)(f))

  /** Handles the response body by providing a stream with the response's data to `f`, regardless of the status code.
    * The stream is always closed after `f` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asStreamAlways[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): StreamResponseAs[T, S with Effect[F]] =
    asStreamAlwaysWithMetadata(s)((s, _) => f(s))

  /** Handles the response body by providing a stream with the response's data, along with the response metadata, to
    * `f`, regardless of the status code. The stream is always closed after `f` completes.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asStreamAlwaysWithMetadata[F[_], T, S](s: Streams[S])(
      f: (s.BinaryStream, ResponseMetadata) => F[T]
  ): StreamResponseAs[T, S with Effect[F]] = StreamResponseAs(ResponseAsStream(s)(f))

  /** Handles the response body by either reading a string (for non-2xx responses), or otherwise returning a stream with
    * the response's data. It's the responsibility of the caller to consume & close the stream.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asStreamUnsafe[S](s: Streams[S]): StreamResponseAs[Either[String, s.BinaryStream], S] =
    asEither(asStringAlways, asStreamAlwaysUnsafe(s))

  /** Handles the response body by returning a stream with the response's data, regardless of the status code. It's the
    * responsibility of the caller to consume & close the stream.
    *
    * A non-blocking, asynchronous streaming implementation must be provided as the [[Streams]] parameter.
    */
  def asStreamAlwaysUnsafe[S](s: Streams[S]): StreamResponseAs[s.BinaryStream, S] =
    StreamResponseAs(ResponseAsStreamUnsafe(s))

  /** Uses the [[StreamResponseAs]] description that matches the condition (using the response's metadata). The
    * conditional response descriptions might include handling the response as a non-blocking, asynchronous stream.
    *
    * This allows using different response description basing on the status code, for example. If none of the conditions
    * match, the default response handling description is used.
    */
  def fromMetadata[T, S](
      default: ResponseAs[T],
      conditions: ConditionalResponseAs[StreamResponseAs[T, S]]*
  ): StreamResponseAs[T, S] =
    StreamResponseAs[T, S](ResponseAsFromMetadata(conditions.map(_.map(_.delegate)).toList, default.delegate))

  /** Uses the `onSuccess` response description for successful responses (2xx), and the `onError` description otherwise.
    *
    * The sucessful response description might include handling the response as a non-blocking, asynchronous stream.
    */
  def asEither[A, B, S](onError: ResponseAs[A], onSuccess: StreamResponseAs[B, S]): StreamResponseAs[Either[A, B], S] =
    fromMetadata[Either[A, B], S](onError.map(Left(_)), ConditionalResponseAs(_.isSuccess, onSuccess.map(Right(_))))
      .showAs(s"either(${onError.show}, ${onSuccess.show})")

  /** Uses `l` to handle the response body. If the raw body value which is used by `l` is replayable (a file or byte
    * array), also uses `r` to read the response body. Otherwise ignores `r` (if the raw body is a stream).
    */
  def asBothOption[A, B, S](l: StreamResponseAs[A, S], r: ResponseAs[B]): StreamResponseAs[(A, Option[B]), S] =
    StreamResponseAs[(A, Option[B]), S](ResponseAsBoth(l.delegate, r.delegate))

  /** Content type will be set to `application/octet-stream`, can be overridden later using the `contentType` method.
    */
  def multipartStream[S](s: Streams[S])(name: String, b: s.BinaryStream): Part[StreamBody[s.BinaryStream, S]] =
    Part(
      name,
      StreamBody(s)(b),
      contentType = Some(MediaType.ApplicationOctetStream)
    )
}
