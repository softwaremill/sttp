package sttp.client

import sttp.capabilities.{Effect, Streams, WebSockets}
import sttp.client.internal._
import sttp.model.StatusCode
import sttp.model.internal.Rfc3986
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

/**
  * Describes how response body should be handled.
  *
  * Apart from the basic cases (ignoring, reading as a byte array, stream or file), response body descriptions can be
  * mapped over, to support custom types. The mapping can take into account the [[ResponseMetadata]], that is the
  * headers and status code. Finally, response description can be determined dynamically depending on the response
  * metadata.
  *
  * A number of `as[Type]` helper methods are available as part of [[SttpApi]] and when importing `sttp.client._`.
  *
  * @tparam T Target type as which the response will be read.
  * @tparam R TODO
  */
sealed trait ResponseAs[+T, -R] {
  def map[T2](f: T => T2): ResponseAs[T2, R] = mapWithMetadata { case (t, _) => f(t) }
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): ResponseAs[T2, R] = MappedResponseAs[T, T2, R](this, f)
}

case object IgnoreResponse extends ResponseAs[Unit, Any]
case object ResponseAsByteArray extends ResponseAs[Array[Byte], Any]

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class ResponseAsStream[F[_], T, Stream, S] private (s: Streams[S], f: Stream => F[T])
    extends ResponseAs[T, Effect[F] with S]
object ResponseAsStream {
  def apply[F[_], T, S](s: Streams[S])(f: s.BinaryStream => F[T]): ResponseAs[T, Effect[F] with S] =
    new ResponseAsStream(s, f)
}

case class ResponseAsStreamUnsafe[BinaryStream, S] private (s: Streams[S]) extends ResponseAs[BinaryStream, S]
object ResponseAsStreamUnsafe {
  def apply[S](s: Streams[S]): ResponseAs[s.BinaryStream, S] = new ResponseAsStreamUnsafe(s)
}

case class ResponseAsFile(output: SttpFile) extends ResponseAs[SttpFile, Any]

sealed trait WebSocketResponseAs[T, -R] extends ResponseAs[T, R]
case class ResponseAsWebSocket[F[_], T](f: WebSocket[F] => F[T])
    extends WebSocketResponseAs[T, Effect[F] with WebSockets]
case class ResponseAsWebSocketUnsafe[F[_]]() extends WebSocketResponseAs[WebSocket[F], Effect[F] with WebSockets]
case class ResponseAsWebSocketStream[S, Pipe[_, _]](s: Streams[S], p: Pipe[WebSocketFrame.Data[_], WebSocketFrame])
    extends WebSocketResponseAs[Unit, S with WebSockets]

case class ConditionalResponseAs[+T, R](condition: ResponseMetadata => Boolean, responseAs: ResponseAs[T, R])
case class ResponseAsFromMetadata[T, R](conditions: List[ConditionalResponseAs[T, R]], default: ResponseAs[T, R])
    extends ResponseAs[T, R] {
  def apply(meta: ResponseMetadata): ResponseAs[T, R] =
    conditions.find(mapping => mapping.condition(meta)).map(_.responseAs).getOrElse(default)
}

case class MappedResponseAs[T, T2, R](raw: ResponseAs[T, R], g: (T, ResponseMetadata) => T2) extends ResponseAs[T2, R] {
  override def mapWithMetadata[T3](f: (T2, ResponseMetadata) => T3): ResponseAs[T3, R] =
    MappedResponseAs[T, T3, R](raw, (t, h) => f(g(t, h), h))
}

object ResponseAs {
  implicit class RichResponseAsEither[A, B, R](ra: ResponseAs[Either[A, B], R]) {
    def mapLeft[L2](f: A => L2): ResponseAs[Either[L2, B], R] = ra.map(_.left.map(f))
    def mapRight[R2](f: B => R2): ResponseAs[Either[A, R2], R] = ra.map(_.right.map(f))
  }

  private[client] def parseParams(s: String, charset: String): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some((Rfc3986.decode()(k, charset), Rfc3986.decode()(v, charset)))
          case _ => None
        }
      )
  }

  /**
    * Returns a function, which maps `Left` values to [[HttpError]]s, and attempts to deserialize `Right` values using
    * the given function, catching any exceptions and representing them as [[DeserializationError]]s.
    */
  def deserializeRightCatchingExceptions[T](
      doDeserialize: String => T
  ): (Either[String, String], ResponseMetadata) => Either[ResponseError[String, Exception], T] = {
    case (Left(s), meta) => Left(HttpError(s, meta.code))
    case (Right(s), _)   => deserializeCatchingExceptions(doDeserialize)(s)
  }

  /**
    * Returns a function, which attempts to deserialize `Right` values using the given function, catching any
    * exceptions and representing them as [[DeserializationError]]s.
    */
  def deserializeCatchingExceptions[T](
      doDeserialize: String => T
  ): String => Either[DeserializationError[Exception], T] =
    deserializeWithError((s: String) =>
      Try(doDeserialize(s)) match {
        case Failure(e: Exception) => Left(e)
        case Failure(t: Throwable) => throw t
        case Success(t)            => Right(t): Either[Exception, T]
      }
    )

  /**
    * Returns a function, which maps `Left` values to [[HttpError]]s, and attempts to deserialize `Right` values using
    * the given function.
    */
  def deserializeRightWithError[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): (Either[String, String], ResponseMetadata) => Either[ResponseError[String, E], T] = {
    case (Left(s), meta) => Left(HttpError(s, meta.code))
    case (Right(s), _)   => deserializeWithError(doDeserialize)(implicitly[ShowError[E]])(s)
  }

  /**
    * Returns a function, which keeps `Left` unchanged, and attempts to deserialize `Right` values using
    * the given function. If deserialization fails, an exception is thrown
    */
  def deserializeRightOrThrow[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): Either[String, String] => Either[String, T] = {
    case Left(s)  => Left(s)
    case Right(s) => Right(deserializeOrThrow(doDeserialize)(implicitly[ShowError[E]])(s))
  }

  /**
    * Converts a deserialization function, which returns errors of type `E`, into a function where errors are wrapped
    * using [[DeserializationError]].
    */
  def deserializeWithError[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): String => Either[DeserializationError[E], T] =
    s =>
      doDeserialize(s) match {
        case Left(e)  => Left(DeserializationError(s, e))
        case Right(b) => Right(b)
      }

  /**
    * Converts a deserialization function, which returns errors of type `E`, into a function where errors are thrown
    * as exceptions, and results are returned unwrapped.
    */
  def deserializeOrThrow[E: ShowError, T](doDeserialize: String => Either[E, T]): String => T =
    s =>
      doDeserialize(s) match {
        case Left(e)  => throw DeserializationError(s, e)
        case Right(b) => b
      }

  def isWebSocket[T, R](ra: ResponseAs[_, _]): Boolean =
    ra match {
      case _: WebSocketResponseAs[_, _] => true
      case ResponseAsFromMetadata(conditions, default) =>
        conditions.exists(c => isWebSocket(c.responseAs)) || isWebSocket(default)
      case MappedResponseAs(raw, _) => isWebSocket(raw)
      case _                        => false
    }
}

sealed abstract class ResponseError[+HE, +DE](error: String) extends Exception(error)
case class HttpError[HE](body: HE, statusCode: StatusCode)
    extends ResponseError[HE, Nothing](s"statusCode: $statusCode, response: $body")
case class DeserializationError[DE: ShowError](body: String, error: DE)
    extends ResponseError[Nothing, DE](implicitly[ShowError[DE]].show(error))

trait ShowError[-T] {
  def show(t: T): String
}

object ShowError {
  implicit val showErrorMessageFromException: ShowError[Exception] = new ShowError[Exception] {
    override def show(t: Exception): String = t.getMessage
  }
}
