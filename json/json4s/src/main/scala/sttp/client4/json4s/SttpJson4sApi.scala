package sttp.client4.json4s

import org.json4s.{Formats, Serialization}
import sttp.client4._
import sttp.client4.internal.Utf8
import sttp.client4.json._
import sttp.model._

trait SttpJson4sApi {

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B <: AnyRef](b: B)(implicit
      formats: Formats,
      serialization: Serialization
  ): StringBody =
    StringBody(serialization.write(b), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseException[String, Exception], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightCatchingExceptions(deserializeJson[B])).showAsJson

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[DeserializationException[Exception], B]] =
    asStringAlways.map(ResponseAs.deserializeCatchingExceptions(deserializeJson[B])).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: Manifest, B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseException[E, Exception], B]] =
    asJson[B].mapLeft { (l: ResponseException[String, Exception]) =>
      l match {
        case HttpError(e, code) =>
          ResponseAs.deserializeCatchingExceptions(deserializeJson[E])(e).fold(identity, HttpError(_, code))
        case de @ DeserializationException(_, _) => de
      }
    }.showAsJsonEither

  def deserializeJson[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): String => B =
    JsonInput.sanitize[B].andThen(serialization.read[B])
}
