package sttp.client4.ziojson

import sttp.client4.DeserializationException
import sttp.client4.HttpError
import sttp.client4.IsOption
import sttp.client4.JsonInput
import sttp.client4.ResponseAs
import sttp.client4.ResponseException
import sttp.client4.ShowError
import sttp.client4.StringBody
import sttp.client4.asString
import sttp.client4.asStringAlways
import sttp.client4.internal.Utf8
import sttp.client4.json.RichResponseAs
import sttp.model.MediaType

trait SttpZioJsonApi extends SttpZioJsonApiExtensions {
  import zio.json._
  private[ziojson] implicit val stringShowError: ShowError[String] = t => t

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B: JsonEncoder](b: B): StringBody = StringBody(b.toJson, Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonDecoder: IsOption]: ResponseAs[Either[ResponseException[String, String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonDecoder: IsOption]: ResponseAs[Either[DeserializationException[String], B]] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: JsonDecoder: IsOption, B: JsonDecoder: IsOption]
      : ResponseAs[Either[ResponseException[E, String], B]] =
    asJson[B].mapLeft {
      case HttpError(e, code) => deserializeJson[E].apply(e).fold(DeserializationException(e, _), HttpError(_, code))
      case de @ DeserializationException(_, _) => de
    }.showAsJsonEither

  def deserializeJson[B: JsonDecoder: IsOption]: String => Either[String, B] =
    JsonInput.sanitize[B].andThen(_.fromJson[B])
}
