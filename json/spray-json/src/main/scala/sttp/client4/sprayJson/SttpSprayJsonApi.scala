package sttp.client4.sprayJson

import spray.json.{DeserializationException => _, _}
import sttp.client4.internal.Utf8
import sttp.client4._
import sttp.client4.json._
import sttp.model._
import sttp.client4.ResponseAs.deserializeEitherOrThrow

trait SttpSprayJsonApi {

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B: JsonWriter](b: B)(implicit printer: JsonPrinter = CompactPrinter): StringBody =
    StringBody(printer(b.toJson), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonReader: IsOption]: ResponseAs[Either[ResponseException[String, Exception], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightCatchingExceptions(deserializeJson[B])).showAsJson

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: JsonReader: IsOption]: ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonReader: IsOption]: ResponseAs[Either[DeserializationException[Exception], B]] =
    asStringAlways.map(ResponseAs.deserializeCatchingExceptions(deserializeJson[B])).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: JsonReader: IsOption, B: JsonReader: IsOption]
      : ResponseAs[Either[ResponseException[E, Exception], B]] =
    asJson[B].mapLeft { (l: ResponseException[String, Exception]) =>
      l match {
        case HttpError(e, code) =>
          ResponseAs.deserializeCatchingExceptions(deserializeJson[E])(e).fold(identity, HttpError(_, code))
        case de @ DeserializationException(_, _) => de
      }
    }.showAsJsonEither

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: JsonReader: IsOption, B: JsonReader: IsOption]: ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata(deserializeEitherOrThrow(deserializeJson[E], deserializeJson[B]))
      .showAsJsonEitherOrFail

  def deserializeJson[B: JsonReader: IsOption]: String => B =
    JsonInput.sanitize[B].andThen((_: String).parseJson.convertTo[B])
}
