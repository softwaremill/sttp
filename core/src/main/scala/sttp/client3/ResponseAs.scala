package sttp.client3

import sttp.capabilities.{Effect, WebSockets}
import sttp.client3.internal.InternalResponseAs
import sttp.model.ResponseMetadata

import scala.util.{Failure, Success, Try}

/** Describes how the response body of a [[Request]] should be handled.
  *
  * Apart from the basic cases (ignoring, reading as a byte array or file), response body descriptions can be mapped
  * over, to support custom types. The mapping can take into account the [[ResponseMetadata]], that is the headers and
  * status code. Responses can also be handled depending on the response metadata. Finally, two response body
  * descriptions can be combined (with some restrictions).
  *
  * A number of `as<Type>` helper methods are available as part of [[SttpApi]] and when importing `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  */
class ResponseAs[+T](private[client3] val internal: InternalResponseAs[T, Any]) extends AbstractResponseAs[T, Any] {
  def map[T2](f: T => T2): ResponseAs[T2] = new ResponseAs(internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): ResponseAs[T2] = new ResponseAs(internal.mapWithMetadata(f))

  def showAs(s: String): ResponseAs[T] = new ResponseAs(internal.showAs(s))
}

object ResponseAs {
  implicit class RichResponseAsEither[A, B](ra: ResponseAs[Either[A, B]]) {
    def mapLeft[L2](f: A => L2): ResponseAs[Either[L2, B]] = ra.map(_.left.map(f))
    def mapRight[R2](f: B => R2): ResponseAs[Either[A, R2]] = ra.map(_.right.map(f))

    /** If the type to which the response body should be deserialized is an `Either[A, B]`:
      *   - in case of `A`, throws as an exception / returns a failed effect (wrapped with an [[HttpError]] if `A` is
      *     not yet an exception)
      *   - in case of `B`, returns the value directly
      */
    def getRight: ResponseAs[B] =
      ra.mapWithMetadata { case (t, meta) =>
        t match {
          case Left(a: Exception) => throw a
          case Left(a)            => throw HttpError(a, meta.code)
          case Right(b)           => b
        }
      }
  }

  implicit class RichResponseAsEitherResponseException[HE, DE, B](
      ra: ResponseAs[Either[ResponseException[HE, DE], B]]
  ) {

    /** If the type to which the response body should be deserialized is an `Either[ResponseException[HE, DE], B]`,
      * either throws the [[DeserializationException]], returns the deserialized body from the [[HttpError]], or the
      * deserialized successful body `B`.
      */
    def getEither: ResponseAs[Either[HE, B]] =
      ra.map {
        case Left(HttpError(he, _))               => Left(he)
        case Left(d: DeserializationException[_]) => throw d
        case Right(b)                             => Right(b)
      }
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
}

/** Describes how the response body of a [[StreamRequest]] should be handled.
  *
  * The stream response can be mapped over, to support custom types. The mapping can take into account the
  * [[ResponseMetadata]], that is the headers and status code.
  *
  * A number of `asStream[Type]` helper methods are available as part of [[SttpApi]] and when importing
  * `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  * @tparam S
  *   The type of stream, used to receive the response body bodies.
  */
class StreamResponseAs[+T, S](private[client3] val internal: InternalResponseAs[T, S])
    extends AbstractResponseAs[T, S] {
  def map[T2](f: T => T2): StreamResponseAs[T2, S] =
    new StreamResponseAs(internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): StreamResponseAs[T2, S] =
    new StreamResponseAs(internal.mapWithMetadata(f))

  def showAs(s: String): StreamResponseAs[T, S] = new StreamResponseAs(internal.showAs(s))
}

/** Describes how the response of a [[WebSocketRequest]] should be handled.
  *
  * The websocket response can be mapped over, to support custom types. The mapping can take into account the
  * [[ResponseMetadata]], that is the headers and status code. Responses can also be handled depending on the response
  * metadata.
  *
  * A number of `asWebSocket` helper methods are available as part of [[SttpApi]] and when importing `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  */
class WebSocketResponseAs[F[_], +T](private[client3] val internal: InternalResponseAs[T, Effect[F] with WebSockets])
    extends AbstractResponseAs[T, Effect[F] with WebSockets] {
  def map[T2](f: T => T2): WebSocketResponseAs[F, T2] =
    new WebSocketResponseAs(internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): WebSocketResponseAs[F, T2] =
    new WebSocketResponseAs(internal.mapWithMetadata(f))

  def showAs(s: String): WebSocketResponseAs[F, T] = new WebSocketResponseAs(internal.showAs(s))
}

/** Describes how the response of a [[WebSocketStreamRequest]] should be handled.
  *
  * The websocket response can be mapped over, to support custom types. The mapping can take into account the
  * [[ResponseMetadata]], that is the headers and status code. Responses can also be handled depending on the response
  * metadata.
  *
  * A number of `asWebSocket` helper methods are available as part of [[SttpApi]] and when importing `sttp.client3._`.
  *
  * @tparam T
  *   Target type as which the response will be read.
  */
class WebSocketStreamResponseAs[+T, S](private[client3] val internal: InternalResponseAs[T, S with WebSockets])
    extends AbstractResponseAs[T, S with WebSockets] {
  def map[T2](f: T => T2): WebSocketStreamResponseAs[T2, S] =
    new WebSocketStreamResponseAs[T2, S](internal.mapWithMetadata { case (t, _) => f(t) })
  def mapWithMetadata[T2](f: (T, ResponseMetadata) => T2): WebSocketStreamResponseAs[T2, S] =
    new WebSocketStreamResponseAs[T2, S](internal.mapWithMetadata(f))

  def showAs(s: String): WebSocketStreamResponseAs[T, S] = new WebSocketStreamResponseAs[T, S](internal.showAs(s))
}

//

/** A wrapper around a ResponseAs to supplement it with a condition on the response metadata.
  *
  * Used in [[SttpApi.fromMetadata()]] to condition the response handler upon the response metadata: status code,
  * headers, etc.
  *
  * @tparam R
  *   The type of response
  */
case class ConditionalResponseAs[+R](condition: ResponseMetadata => Boolean, responseAs: R) {
  def map[R2](f: R => R2): ConditionalResponseAs[R2] = ConditionalResponseAs(condition, f(responseAs))
}
