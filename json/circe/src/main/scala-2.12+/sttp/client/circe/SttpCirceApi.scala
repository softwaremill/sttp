package sttp.client.circe

import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Printer}
import sttp.client.internal.Utf8
import sttp.client.{IsOption, ResponseAs, ResponseError, _}
import sttp.model.MediaType

trait SttpCirceApi {

  implicit def circeBodySerializer[B](implicit
      encoder: Encoder[B],
      printer: Printer = Printer.noSpaces
  ): BodySerializer[B] =
    b => StringBody(encoder(b).printWith(printer), Utf8, Some(MediaType.ApplicationJson))

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Decoder: IsOption]: ResponseAs[Either[ResponseError[io.circe.Error], B], Nothing] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson))

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Decoder: IsOption]: ResponseAs[Either[DeserializationError[io.circe.Error], B], Nothing] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson))

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns the parse
    * result, or throws an exception is there's an error during deserialization
    */
  def asJsonAlwaysUnsafe[B: Decoder: IsOption]: ResponseAs[B, Nothing] =
    asStringAlways.map(ResponseAs.deserializeOrThrow(deserializeJson))

  def deserializeJson[B: Decoder: IsOption]: String => Either[io.circe.Error, B] =
    JsonInput.sanitize[B].andThen(decode[B])
}
