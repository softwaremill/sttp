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
    b => StringBody(serialization.write(b), Utf8, Some(MediaType.ApplicationJson))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseException[String, Exception], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightCatchingExceptions(deserializeJson[B]))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(String)` if the response code was other than 2xx (deserialization is not attempted)
    * - throws an exception if there's an error during deserialization
    */
  def asJsonUnsafe[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[String, B], Any] = asString.mapRight(deserializeJson)

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[DeserializationException[Exception], B], Any] =
    asStringAlways.map(ResponseAs.deserializeCatchingExceptions(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns the parse
    * result, or throws an exception is there's an error during deserialization
    */
  def asJsonAlwaysUnsafe[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[B, Any] =
    asStringAlways.map(deserializeJson)

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonEither[E: Manifest, B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseException[E, Exception], B], Any] = {
    asEitherDeserialized(asJsonAlways[E], asJsonAlways[B])
  }

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(E)` if the response was other than 2xx and parsing was successful
    * - throws an exception if there's an error during deserialization
    */
  def asJsonEitherUnsafe[E: Manifest, B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): ResponseAs[Either[E, B], Any] = {
    asEither(asJsonAlwaysUnsafe[E], asJsonAlwaysUnsafe[B])
  }

  def deserializeJson[B: Manifest](implicit
      formats: Formats,
      serialization: Serialization
  ): String => B =
    JsonInput.sanitize[B].andThen(serialization.read[B])
}
