package sttp.client.circe

import sttp.client._
import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Printer}
import sttp.client.internal.Utf8
import sttp.model.MediaType

trait SttpCirceApi {

  implicit def circeBodySerializer[B](implicit
      encoder: Encoder[B],
      printer: Printer = Printer.noSpaces
  ): BodySerializer[B] =
    b => StringBody(encoder(b).pretty(printer), Utf8, Some(MediaType.ApplicationJson))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Decoder: IsOption]: ResponseAs[Either[ResponseException[String, io.circe.Error], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(String)` if the response code was other than 2xx (deserialization is not attempted)
    * - throws an exception if there's an error during deserialization
    */
  def asJsonUnsafe[B: Decoder: IsOption]: ResponseAs[Either[String, B], Any] =
    asString.map(ResponseAs.deserializeRightOrThrow(deserializeJson))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Decoder: IsOption]: ResponseAs[Either[DeserializationException[io.circe.Error], B], Any] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns the parse
    * result, or throws an exception is there's an error during deserialization
    */
  def asJsonAlwaysUnsafe[B: Decoder: IsOption]: ResponseAs[B, Any] =
    asStringAlways.map(ResponseAs.deserializeOrThrow(deserializeJson))

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonEither[E: Decoder: IsOption, B: Decoder: IsOption]
      : ResponseAs[Either[ResponseException[E, io.circe.Error], B], Any] = {
    asEitherDeserialized(asJsonAlways[E], asJsonAlways[B])
  }

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(E)` if the response was other than 2xx and parsing was successful
    * - throws an exception if there's an error during deserialization
    */
  def asJsonEitherUnsafe[E: Decoder: IsOption, B: Decoder: IsOption]: ResponseAs[Either[E, B], Any] = {
    asEither(asJsonAlwaysUnsafe[E], asJsonAlwaysUnsafe[B])
  }

  def deserializeJson[B: Decoder: IsOption]: String => Either[io.circe.Error, B] =
    JsonInput.sanitize[B].andThen(decode[B])
}
