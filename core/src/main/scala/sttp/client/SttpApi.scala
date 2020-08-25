package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.capabilities.{Effect, Streams, WebSockets}
import sttp.client.internal._
import sttp.model._
import sttp.client.internal.SttpFile
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.collection.immutable.Seq
import scala.concurrent.duration._

trait SttpApi extends SttpExtensions with UriInterpolator {
  val DefaultReadTimeout: Duration = 1.minute

  /**
    * An empty request with no headers.
    *
    * Reads the response body as an `Either[String, String]`, where `Left` is used if the status code is non-2xx,
    * and `Right` otherwise.
    */
  val emptyRequest: RequestT[Empty, Either[String, String], Any] =
    RequestT[Empty, Either[String, String], Any](
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
    * A starting request, with the following modification comparing to `emptyRequest`: `Accept-Encoding` is set to
    * `gzip, deflate` (compression/decompression is handled automatically by the library).
    *
    * Reads the response body as an `Either[String, String]`, where `Left` is used if the status code is non-2xx,
    * and `Right` otherwise.
    */
  val basicRequest: RequestT[Empty, Either[String, String], Any] =
    emptyRequest.acceptEncoding("gzip, deflate")

  /**
    * A starting request which always reads the response body as a string, regardless of the status code.
    */
  val quickRequest: RequestT[Empty, String, Any] = basicRequest.response(asStringAlways)

  // response specifications

  def ignore: ResponseAs[Unit, Any] = IgnoreResponse

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asString: ResponseAs[Either[String, String], Any] = asString(Utf8)

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asStringAlways: ResponseAs[String, Any] = asStringAlways(Utf8)

  /**
    * Use the given charset by default, unless specified otherwise in the response headers.
    */
  def asString(charset: String): ResponseAs[Either[String, String], Any] =
    asStringAlways(charset).mapWithMetadata { (s, m) =>
      if (m.isSuccess) Right(s) else Left(s)
    }

  def asStringAlways(charset: String): ResponseAs[String, Any] =
    asByteArrayAlways.mapWithMetadata { (bytes, metadata) =>
      val charset2 = metadata.contentType.flatMap(charsetFromContentType).getOrElse(charset)
      val charset3 = sanitizeCharset(charset2)
      new String(bytes, charset3)
    }

  def asByteArray: ResponseAs[Either[String, Array[Byte]], Any] = asEither(asStringAlways, asByteArrayAlways)

  def asByteArrayAlways: ResponseAs[Array[Byte], Any] = ResponseAsByteArray

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asParams: ResponseAs[Either[String, Seq[(String, String)]], Any] = asParams(Utf8)

  /**
    * Use the `utf-8` charset by default, unless specified otherwise in the response headers.
    */
  def asParamsAlways: ResponseAs[Seq[(String, String)], Any] = asParamsAlways(Utf8)

  /**
    * Use the given charset by default, unless specified otherwise in the response headers.
    */
  def asParams(charset: String): ResponseAs[Either[String, Seq[(String, String)]], Any] = {
    asEither(asStringAlways, asParamsAlways(charset))
  }

  /**
    * Use the given charset by default, unless specified otherwise in the response headers.
    */
  def asParamsAlways(charset: String): ResponseAs[Seq[(String, String)], Any] = {
    val charset2 = sanitizeCharset(charset)
    asStringAlways(charset2).map(ResponseAs.parseParams(_, charset2))
  }

  def asStream[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): ResponseAs[Either[String, T], Effect[F] with S] =
    asEither(asStringAlways, asStreamAlways(s)(f))

  def asStreamAlways[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): ResponseAs[T, Effect[F] with S] =
    ResponseAsStream(s)(f)

  def asStreamUnsafe[S](s: Streams[S]): ResponseAs[Either[String, s.BinaryStream], S] =
    asEither(asStringAlways, asStreamAlwaysUnsafe(s))

  def asStreamAlwaysUnsafe[S](s: Streams[S]): ResponseAs[s.BinaryStream, S] = ResponseAsStreamUnsafe(s)

  private[client] def asSttpFile(file: SttpFile): ResponseAs[SttpFile, Any] =
    ResponseAsFile(file)

  def asWebSocket[F[_], T](f: WebSocket[F] => F[T]): ResponseAs[Either[String, T], Effect[F] with WebSockets] =
    asWebSocketEither(asStringAlways, asWebSocketAlways(f))

  def asWebSocketAlways[F[_], T](f: WebSocket[F] => F[T]): ResponseAs[T, Effect[F] with WebSockets] =
    ResponseAsWebSocket(f)

  def asWebSocketUnsafe[F[_]]: ResponseAs[Either[String, WebSocket[F]], Effect[F] with WebSockets] =
    asWebSocketEither(asStringAlways, asWebSocketAlwaysUnsafe)

  def asWebSocketAlwaysUnsafe[F[_]]: ResponseAs[WebSocket[F], Effect[F] with WebSockets] = ResponseAsWebSocketUnsafe()

  def asWebSocketStream[S](
      s: Streams[S]
  )(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): ResponseAs[Either[String, Unit], S with WebSockets] =
    asWebSocketEither(asStringAlways, asWebSocketStreamAlways(s)(p))

  def asWebSocketStreamAlways[S](s: Streams[S])(
      p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
  ): ResponseAs[Unit, S with WebSockets] = ResponseAsWebSocketStream(s, p)

  def fromMetadata[T, R](default: ResponseAs[T, R], conditions: ConditionalResponseAs[T, R]*): ResponseAs[T, R] =
    ResponseAsFromMetadata(conditions.toList, default)

  /**
    * Uses the `onSuccess` response specification for successful responses (2xx), and the `onError`
    * specification otherwise.
    */
  def asEither[A, B, R](onError: ResponseAs[A, R], onSuccess: ResponseAs[B, R]): ResponseAs[Either[A, B], R] =
    fromMetadata(onError.map(Left(_)), ConditionalResponseAs(_.isSuccess, onSuccess.map(Right(_))))

  /**
    * Uses the `onSuccess` response specification for 101 responses (switching protocols), and the `onError`
    * specification otherwise.
    */
  def asWebSocketEither[A, B, R](onError: ResponseAs[A, R], onSuccess: ResponseAs[B, R]): ResponseAs[Either[A, B], R] =
    fromMetadata(
      onError.map(Left(_)),
      ConditionalResponseAs(_.code == StatusCode.SwitchingProtocols, onSuccess.map(Right(_)))
    )

  /**
    * Use both `l` and `r` to read the response body. Neither response specifications may use streaming or web sockets.
    */
  def asBoth[A, B](l: ResponseAs[A, Any], r: ResponseAs[B, Any]): ResponseAs[(A, B), Any] =
    asBothOption(l, r).map {
      case (a, bo) =>
        // since l has no requirements, we know that the body will be replayable
        (a, bo.get)
    }

  /**
    * Use `l` to read the response body. If the raw body value which is used by `l` is replayable (a file or byte
    * array), also use `r` to read the response body. Otherwise ignore `r` (if the raw body is a stream or
    * a web socket).
    */
  def asBothOption[A, B, R](l: ResponseAs[A, R], r: ResponseAs[B, Any]): ResponseAs[(A, Option[B]), R] =
    ResponseAsBoth(l, r)

  // multipart factory methods

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String): Part[BasicRequestBody] =
    Part(name, StringBody(data, Utf8), contentType = Some(MediaType.TextPlainUtf8))

  /**
    * Content type will be set to `text/plain` with `utf-8` encoding, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, data: String, encoding: String): Part[BasicRequestBody] =
    Part(name, StringBody(data, encoding), contentType = Some(MediaType.TextPlainUtf8))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: Array[Byte]): Part[BasicRequestBody] =
    Part(name, ByteArrayBody(data), contentType = Some(MediaType.ApplicationOctetStream))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: ByteBuffer): Part[BasicRequestBody] =
    Part(name, ByteBufferBody(data), contentType = Some(MediaType.ApplicationOctetStream))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipart(name: String, data: InputStream): Part[BasicRequestBody] =
    Part(name, InputStreamBody(data), contentType = Some(MediaType.ApplicationOctetStream))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    *
    * File name will be set to the name of the file.
    */
  private[client] def multipartSttpFile(name: String, file: SttpFile): Part[BasicRequestBody] =
    Part(name, FileBody(file), fileName = Some(file.name), contentType = Some(MediaType.ApplicationOctetStream))

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String]): Part[BasicRequestBody] =
    Part(
      name,
      RequestBody.paramsToStringBody(fs.toList, Utf8),
      contentType = Some(MediaType.ApplicationXWwwFormUrlencoded)
    )

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Map[String, String], encoding: String): Part[BasicRequestBody] =
    Part(
      name,
      RequestBody.paramsToStringBody(fs.toList, encoding),
      contentType = Some(MediaType.ApplicationXWwwFormUrlencoded)
    )

  /**
    * Encodes the given parameters as form data using `utf-8`.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)]): Part[BasicRequestBody] =
    Part(name, RequestBody.paramsToStringBody(fs, Utf8), contentType = Some(MediaType.ApplicationXWwwFormUrlencoded))

  /**
    * Encodes the given parameters as form data.
    *
    * Content type will be set to `application/x-www-form-urlencoded`, can be
    * overridden later using the `contentType` method.
    */
  def multipart(name: String, fs: Seq[(String, String)], encoding: String): Part[BasicRequestBody] =
    Part(
      name,
      RequestBody.paramsToStringBody(fs, encoding),
      contentType = Some(MediaType.ApplicationXWwwFormUrlencoded)
    )

  /**
    * Content type will be set to `application/octet-stream`, can be
    * overridden later using the `contentType` method.
    */
  def multipart[B: BodySerializer](name: String, b: B): Part[BasicRequestBody] =
    Part(name, implicitly[BodySerializer[B]].apply(b), contentType = Some(MediaType.ApplicationXWwwFormUrlencoded))

  /**
    * Content type will be set to `application/octet-stream`, can be overridden
    * later using the `contentType` method.
    */
  def multipartStream[S](s: Streams[S])(name: String, b: s.BinaryStream): Part[RequestBody[S]] =
    Part(
      name,
      StreamBody(s)(b),
      contentType = Some(MediaType.ApplicationOctetStream)
    )
}
