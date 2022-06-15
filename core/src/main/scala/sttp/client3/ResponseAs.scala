package sttp.client3

import sttp.capabilities.{Effect, Streams, WebSockets}
import sttp.client3.internal._
import sttp.model.{ResponseMetadata, StatusCode}
import sttp.model.internal.Rfc3986
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

/** Describes how response body should be handled.
  *
  * Apart from the basic cases (ignoring, reading as a byte array or file), response body descriptions can be mapped
  * over, to support custom types. The mapping can take into account the [[ResponseMetadata]], that is the headers and
  * status code. Responses can also be handled depending on the response metadata. Finally, two response body
  * descriptions can be combined (with some restrictions).
  *
  * A number of `as[Type]` helper methods are available as part of [[SttpApi]] and when importing `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  * @tparam R
  *   The backend capabilities required by the response description. This might be `Any` (no requirements), [[Effect]]
  *   (the backend must support the given effect type), [[Streams]] (the ability to send and receive streaming bodies)
  *   or [[WebSockets]] (the ability to handle websocket requests).
  */
sealed trait ResponseAs[+T, -R] {
  def map[T2](f: T => T2): ResponseAs[T2, R] = mapWithMetadata { case (t, _) => f(t) }
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): ResponseAs[T2, R] =
    MappedResponseAs[T, T2, R](this, f, None)

  def show: String
  def showAs(s: String): ResponseAs[T, R] = MappedResponseAs[T, T, R](this, (t, _) => t, Some(s))
}

case object IgnoreResponse extends ResponseAs[Unit, Any] {
  override def show: String = "ignore"
}
case object ResponseAsByteArray extends ResponseAs[Array[Byte], Any] {
  override def show: String = "as byte array"
}

// Path-dependent types are not supported in constructor arguments or the extends clause. Thus we cannot express the
// fact that `BinaryStream =:= s.BinaryStream`. We have to rely on correct construction via the companion object and
// perform typecasts when the request is deconstructed.
case class ResponseAsStream[F[_], T, Stream, S] private (s: Streams[S], f: (Stream, ResponseMetadata) => F[T])
    extends ResponseAs[T, Effect[F] with S] {
  override def show: String = "as stream"
}
object ResponseAsStream {
  def apply[F[_], T, S](s: Streams[S])(f: (s.BinaryStream, ResponseMetadata) => F[T]): ResponseAs[T, Effect[F] with S] =
    new ResponseAsStream(s, f)
}

case class ResponseAsStreamUnsafe[BinaryStream, S] private (s: Streams[S]) extends ResponseAs[BinaryStream, S] {
  override def show: String = "as stream unsafe"
}
object ResponseAsStreamUnsafe {
  def apply[S](s: Streams[S]): ResponseAs[s.BinaryStream, S] = new ResponseAsStreamUnsafe(s)
}

case class ResponseAsFile(output: SttpFile) extends ResponseAs[SttpFile, Any] {
  override def show: String = s"as file: ${output.name}"
}

sealed trait WebSocketResponseAs[T, -R] extends ResponseAs[T, R]
case class ResponseAsWebSocket[F[_], T](f: (WebSocket[F], ResponseMetadata) => F[T])
    extends WebSocketResponseAs[T, Effect[F] with WebSockets] {
  override def show: String = "as web socket"
}
case class ResponseAsWebSocketUnsafe[F[_]]() extends WebSocketResponseAs[WebSocket[F], Effect[F] with WebSockets] {
  override def show: String = "as web socket unsafe"
}
case class ResponseAsWebSocketStream[S, Pipe[_, _]](s: Streams[S], p: Pipe[WebSocketFrame.Data[_], WebSocketFrame])
    extends WebSocketResponseAs[Unit, S with WebSockets] {
  override def show: String = "as web socket stream"
}

case class ConditionalResponseAs[+T, R](condition: ResponseMetadata => Boolean, responseAs: ResponseAs[T, R])
case class ResponseAsFromMetadata[T, R](conditions: List[ConditionalResponseAs[T, R]], default: ResponseAs[T, R])
    extends ResponseAs[T, R] {
  def apply(meta: ResponseMetadata): ResponseAs[T, R] =
    conditions.find(mapping => mapping.condition(meta)).map(_.responseAs).getOrElse(default)
  override def show: String = s"either(${(default.show :: conditions.map(_.responseAs.show)).mkString(", ")})"
}

case class MappedResponseAs[T, T2, R](raw: ResponseAs[T, R], g: (T, ResponseMetadata) => T2, showAs: Option[String])
    extends ResponseAs[T2, R] {
  override def mapWithMetadata[T3](f: (T2, ResponseMetadata) => T3): ResponseAs[T3, R] =
    MappedResponseAs[T, T3, R](raw, (t, h) => f(g(t, h), h), showAs.map(s => s"mapped($s)"))
  override def showAs(s: String): ResponseAs[T2, R] = this.copy(showAs = Some(s))

  override def show: String = showAs.getOrElse(s"mapped(${raw.show})")
}

case class ResponseAsBoth[A, B, R](l: ResponseAs[A, R], r: ResponseAs[B, Any]) extends ResponseAs[(A, Option[B]), R] {
  override def show: String = s"(${l.show}, optionally ${r.show})"
}

object ResponseAs {
  implicit class RichResponseAsEither[A, B, R](ra: ResponseAs[Either[A, B], R]) {
    def mapLeft[L2](f: A => L2): ResponseAs[Either[L2, B], R] = ra.map(_.left.map(f))
    def mapRight[R2](f: B => R2): ResponseAs[Either[A, R2], R] = ra.map(_.right.map(f))

    /** If the type to which the response body should be deserialized is an `Either[A, B]`:
      *   - in case of `A`, throws as an exception / returns a failed effect (wrapped with an [[HttpError]] if `A` is
      *     not yet an exception)
      *   - in case of `B`, returns the value directly
      */
    def getRight: ResponseAs[B, R] =
      ra.mapWithMetadata { case (t, meta) =>
        t match {
          case Left(a: Exception) => throw a
          case Left(a)            => throw HttpError(a, meta.code)
          case Right(b)           => b
        }
      }
  }

  implicit class RichResponseAsEitherResponseException[HE, DE, B, R](
      ra: ResponseAs[Either[ResponseException[HE, DE], B], R]
  ) {

    /** If the type to which the response body should be deserialized is an `Either[ResponseException[HE, DE], B]`,
      * either throws the [[DeserializationException]], returns the deserialized body from the [[HttpError]], or the
      * deserialized successful body `B`.
      */
    def getEither: ResponseAs[Either[HE, B], R] =
      ra.map {
        case Left(HttpError(he, _))               => Left(he)
        case Left(d: DeserializationException[_]) => throw d
        case Right(b)                             => Right(b)
      }
  }

  private[client3] def parseParams(s: String, charset: String): Seq[(String, String)] = {
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

  /** Returns a function, which maps `Left` values to [[HttpError]] s, and attempts to deserialize `Right` values using
    * the given function, catching any exceptions and representing them as [[DeserializationException]] s.
    */
  def deserializeRightCatchingExceptions[T](
      doDeserialize: String => T
  ): (Either[String, String], ResponseMetadata) => Either[ResponseException[String, Exception], T] = {
    case (Left(s), meta) => Left(HttpError(s, meta.code))
    case (Right(s), _)   => deserializeCatchingExceptions(doDeserialize)(s)
  }

  /** Returns a function, which attempts to deserialize `Right` values using the given function, catching any exceptions
    * and representing them as [[DeserializationException]] s.
    */
  def deserializeCatchingExceptions[T](
      doDeserialize: String => T
  ): String => Either[DeserializationException[Exception], T] =
    deserializeWithError((s: String) =>
      Try(doDeserialize(s)) match {
        case Failure(e: Exception) => Left(e)
        case Failure(t: Throwable) => throw t
        case Success(t)            => Right(t): Either[Exception, T]
      }
    )

  /** Returns a function, which maps `Left` values to [[HttpError]] s, and attempts to deserialize `Right` values using
    * the given function.
    */
  def deserializeRightWithError[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): (Either[String, String], ResponseMetadata) => Either[ResponseException[String, E], T] = {
    case (Left(s), meta) => Left(HttpError(s, meta.code))
    case (Right(s), _)   => deserializeWithError(doDeserialize)(implicitly[ShowError[E]])(s)
  }

  /** Returns a function, which keeps `Left` unchanged, and attempts to deserialize `Right` values using the given
    * function. If deserialization fails, an exception is thrown
    */
  def deserializeRightOrThrow[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): Either[String, String] => Either[String, T] = {
    case Left(s)  => Left(s)
    case Right(s) => Right(deserializeOrThrow(doDeserialize)(implicitly[ShowError[E]])(s))
  }

  /** Converts a deserialization function, which returns errors of type `E`, into a function where errors are wrapped
    * using [[DeserializationException]].
    */
  def deserializeWithError[E: ShowError, T](
      doDeserialize: String => Either[E, T]
  ): String => Either[DeserializationException[E], T] =
    s =>
      doDeserialize(s) match {
        case Left(e)  => Left(DeserializationException(s, e))
        case Right(b) => Right(b)
      }

  /** Converts a deserialization function, which returns errors of type `E`, into a function where errors are thrown as
    * exceptions, and results are returned unwrapped.
    */
  def deserializeOrThrow[E: ShowError, T](doDeserialize: String => Either[E, T]): String => T =
    s =>
      doDeserialize(s) match {
        case Left(e)  => throw DeserializationException(s, e)
        case Right(b) => b
      }

  def isWebSocket[T, R](ra: ResponseAs[_, _]): Boolean =
    ra match {
      case _: WebSocketResponseAs[_, _] => true
      case ResponseAsFromMetadata(conditions, default) =>
        conditions.exists(c => isWebSocket(c.responseAs)) || isWebSocket(default)
      case MappedResponseAs(raw, _, _) => isWebSocket(raw)
      case ResponseAsBoth(l, r)        => isWebSocket(l) || isWebSocket(r)
      case _                           => false
    }
}

sealed abstract class ResponseException[+HE, +DE](error: String) extends Exception(error)
case class HttpError[HE](body: HE, statusCode: StatusCode)
    extends ResponseException[HE, Nothing](s"statusCode: $statusCode, response: $body")
case class DeserializationException[DE: ShowError](body: String, error: DE)
    extends ResponseException[Nothing, DE](implicitly[ShowError[DE]].show(error))

object HttpError {
  @tailrec def find(exception: Throwable): Option[HttpError[_]] =
    Option(exception) match {
      case Some(error: HttpError[_]) => Some(error)
      case Some(_)                   => find(exception.getCause)
      case None                      => Option.empty
    }
}

trait ShowError[-T] {
  def show(t: T): String
}

object ShowError {
  implicit val showErrorMessageFromException: ShowError[Exception] = new ShowError[Exception] {
    override def show(t: Exception): String = t.getMessage
  }
}
