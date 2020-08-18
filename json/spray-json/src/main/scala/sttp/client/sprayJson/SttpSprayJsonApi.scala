package sttp.client.sprayJson

import spray.json.{DeserializationException => _, _}
import sttp.client.internal.Utf8
import sttp.client.{IsOption, ResponseAs, asEither, _}
import sttp.model._

trait SttpSprayJsonApi {
  implicit def sprayBodySerializer[B: JsonWriter](implicit printer: JsonPrinter = CompactPrinter): BodySerializer[B] =
    b => StringBody(printer(b.toJson), Utf8, Some(MediaType.ApplicationJson))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: JsonReader: IsOption]: ResponseAs[Either[ResponseException[String, Exception], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightCatchingExceptions(deserializeJson[B]))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(String)` if the response code was other than 2xx (deserialization is not attempted)
    * - throws an exception if there's an error during deserialization
    */
  def asJsonUnsafe[B: JsonReader: IsOption]: ResponseAs[Either[String, B], Any] =
    asString.mapRight(deserializeJson)

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonReader: IsOption]: ResponseAs[Either[DeserializationException[Exception], B], Any] =
    asStringAlways.map(ResponseAs.deserializeCatchingExceptions(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns the parse
    * result, or throws an exception is there's an error during deserialization
    */
  def asJsonAlwaysUnsafe[B: JsonReader: IsOption]: ResponseAs[B, Any] =
    asStringAlways.map(deserializeJson)

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonEither[E: JsonReader: IsOption, B: JsonReader: IsOption]
      : ResponseAs[Either[ResponseException[E, Exception], B], Any] = {
    asEitherDeserialized(asJsonAlways[E], asJsonAlways[B])
  }

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(E)` if the response was other than 2xx and parsing was successful
    * - throws an exception if there's an error during deserialization
    */
  def asJsonEitherUnsafe[E: JsonReader: IsOption, B: JsonReader: IsOption]: ResponseAs[Either[E, B], Any] = {
    asEither(asJsonAlwaysUnsafe[E], asJsonAlwaysUnsafe[B])
  }

  def deserializeJson[B: JsonReader: IsOption]: String => B =
    JsonInput.sanitize[B].andThen((_: String).parseJson.convertTo[B])
}
