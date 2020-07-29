package sttp.client

import java.io.InputStream
import java.nio.ByteBuffer

import sttp.client.internal._
import sttp.model._
import sttp.client.internal.SttpFile
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame

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
      isWebSocket = false,
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
    * Use the given charset by default, unless specified otherwise in the response headers.
    */
  def asParams(charset: String): ResponseAs[Either[String, Seq[(String, String)]], Any] = {
    val charset2 = sanitizeCharset(charset)
    asString(charset2).mapRight(ResponseAs.parseParams(_, charset2))
  }

  def asStream[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): ResponseAs[Either[String, T], Effect[F] with S] =
    asEither(asStringAlways, asStreamAlways(s)(f))

  def asStreamAlways[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): ResponseAs[T, Effect[F] with S] =
    ResponseAsStream(s)(f)

  def asStreamUnsafe[S](s: Streams[S]): ResponseAs[Either[String, s.BinaryStream], S] =
    asEither(asStringAlways, asStreamUnsafeAlways(s))

  def asStreamUnsafeAlways[S](s: Streams[S]): ResponseAs[s.BinaryStream, S] = ResponseAsStreamUnsafe(s)

  private[client] def asSttpFile(file: SttpFile): ResponseAs[SttpFile, Any] =
    ResponseAsFile(file)

  def asWebSocket[F[_], T](f: WebSocket[F] => F[T]): ResponseAs[Either[String, T], Effect[F] with WebSockets] =
    asWebSocketEither(asStringAlways, asWebSocketAlways(f))

  def asWebSocketAlways[F[_], T](f: WebSocket[F] => F[T]): ResponseAs[T, Effect[F] with WebSockets] =
    ResponseAsWebSocket(f)

  def asWebSocketUnsafe[F[_]]: ResponseAs[Either[String, WebSocket[F]], Effect[F] with WebSockets] =
    asWebSocketEither(asStringAlways, asWebSocketUnsafeAlways)

  def asWebSocketUnsafeAlways[F[_]]: ResponseAs[WebSocket[F], Effect[F] with WebSockets] = ResponseAsWebSocketUnsafe()

  def asWebSocketStream[S](
      s: Streams[S]
  )(p: s.Pipe[WebSocketFrame, WebSocketFrame]): ResponseAs[Either[String, Unit], S with WebSockets] =
    asWebSocketEither(asStringAlways, asWebSocketStreamAlways(s)(p))

  def asWebSocketStreamAlways[S](s: Streams[S])(
      p: s.Pipe[WebSocketFrame, WebSocketFrame]
  ): ResponseAs[Unit, S with WebSockets] = ResponseAsWebSocketStream(s, p)

  def fromMetadata[T, R](f: ResponseMetadata => ResponseAs[T, R]): ResponseAs[T, R] = ResponseAsFromMetadata(f)

  def asEither[A, B, R](onError: ResponseAs[A, R], onSuccess: ResponseAs[B, R]): ResponseAs[Either[A, B], R] =
    fromMetadata { meta => if (meta.isSuccess) onSuccess.map(Right(_)) else onError.map(Left(_)) }

  def asWebSocketEither[A, B, R](onError: ResponseAs[A, R], onSuccess: ResponseAs[B, R]): ResponseAs[Either[A, B], R] =
    fromMetadata { meta =>
      if (meta.code == StatusCode.SwitchingProtocols) onSuccess.map(Right(_)) else onError.map(Left(_))
    }

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
}
