package sttp.client4.ziojson

import sttp.client4.DeserializationException
import sttp.client4.HttpError
import sttp.client4.IsOption
import sttp.client4.JsonInput
import sttp.client4.ResponseAs
import sttp.client4.ResponseException
import sttp.client4.StringBody
import sttp.client4.asString
import sttp.client4.asStringAlways
import sttp.client4.internal.Utf8
import sttp.client4.json.RichResponseAs
import sttp.model.MediaType

trait SttpZioJsonApi extends SttpZioJsonApiExtensions {
  import zio.json._

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B: JsonEncoder](b: B): StringBody = StringBody(b.toJson, Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonDecoder: IsOption]: ResponseAs[Either[ResponseException[String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: JsonDecoder: IsOption]: ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonDecoder: IsOption]: ResponseAs[Either[DeserializationException, B]] =
    asStringAlways.mapWithMetadata(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: JsonDecoder: IsOption, B: JsonDecoder: IsOption]: ResponseAs[Either[ResponseException[E], B]] =
    asJson[B].mapLeft { (l: ResponseException[String]) =>
      l match {
        case HttpError(e, meta) =>
          deserializeJson[E].apply(e).fold(DeserializationException(e, _, meta), HttpError(_, meta))
        case de: DeserializationException => de
      }
    }.showAsJsonEither

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: JsonDecoder: IsOption, B: JsonDecoder: IsOption]: ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata(ResponseAs.deserializeEitherWithErrorOrThrow(deserializeJson[E], deserializeJson[B]))
      .showAsJsonEitherOrFail

  def deserializeJson[B: JsonDecoder: IsOption]: String => Either[Exception, B] =
    JsonInput.sanitize[B].andThen(_.fromJson[B].left.map(ZioJsonException(_)))

  case class ZioJsonException(error: String) extends Exception
}
