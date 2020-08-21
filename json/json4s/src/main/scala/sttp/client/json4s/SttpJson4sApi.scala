package sttp.client.json4s

import org.json4s.{Formats, Serialization}
import sttp.client.{ResponseAs, _}
import sttp.client.internal.Utf8
import sttp.model._

trait SttpJson4sApi {
  implicit def json4sBodySerializer[B <: AnyRef](implicit
      formats: Formats,
      serialization: Serialization
  ): BodySerializer[B] =
    b => StringBody(serialization.write(b), Utf8, MediaType.ApplicationJson)

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseException[String, Exception], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightCatchingExceptions(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[DeserializationException[Exception], B], Any] =
    asStringAlways.map(ResponseAs.deserializeCatchingExceptions(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: Manifest, B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseException[E, Exception], B], Any] = {
    asJson[B].mapLeft {
      case HttpError(e, code) =>
        ResponseAs.deserializeCatchingExceptions(deserializeJson[E])(e).fold(identity, HttpError(_, code))
    }
  }

  def deserializeJson[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): String => B =
    JsonInput.sanitize[B].andThen(serialization.read[B])
}
