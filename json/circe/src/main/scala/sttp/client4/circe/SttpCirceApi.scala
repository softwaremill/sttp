package sttp.client4.circe

import sttp.client4._
import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Printer}
import sttp.client4.internal.Utf8
import sttp.model.MediaType
import sttp.client4.json._

trait SttpCirceApi {

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B](b: B)(implicit
      encoder: Encoder[B],
      printer: Printer = Printer.noSpaces
  ): StringBody =
    StringBody(encoder(b).printWith(printer), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: Decoder: IsOption]: ResponseAs[Either[ResponseException[String, io.circe.Error], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: Decoder: IsOption]: ResponseAs[Either[DeserializationException[io.circe.Error], B]] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: Decoder: IsOption, B: Decoder: IsOption]
      : ResponseAs[Either[ResponseException[E, io.circe.Error], B]] =
    asJson[B].mapLeft {
      case HttpError(e, code) => deserializeJson[E].apply(e).fold(DeserializationException(e, _), HttpError(_, code))
      case de @ DeserializationException(_, _) => de
    }.showAsJsonEither

  def deserializeJson[B: Decoder: IsOption]: String => Either[io.circe.Error, B] =
    JsonInput.sanitize[B].andThen(decode[B])
}
