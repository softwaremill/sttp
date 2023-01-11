package sttp.client3

import sttp.client3.internal.InternalResponseAs
import sttp.model.ResponseMetadata

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

  def show: String = internal.show
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
}
