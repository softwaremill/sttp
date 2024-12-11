package sttp.client4.playJson

import play.api.libs.json.{JsError, Json, Reads, Writes}
import sttp.client4.internal.Utf8
import sttp.client4.json._
import sttp.client4._
import sttp.model.MediaType

import scala.util.{Failure, Success, Try}
import sttp.client4.ResponseAs.deserializeEitherWithErrorOrThrow

trait SttpPlayJsonApi {
  implicit val errorMessageForPlayError: ShowError[JsError] = new ShowError[JsError] {
    override def show(t: JsError): String = t.errors.mkString(",")
  }

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B: Writes](b: B): StringBody =
    StringBody(Json.stringify(Json.toJson(b)), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: Reads: IsOption]: ResponseAs[Either[ResponseException[String, JsError], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson[B])).showAsJson

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: Reads: IsOption]: ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: Reads: IsOption]: ResponseAs[Either[DeserializationException[JsError], B]] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson[B])).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: Reads: IsOption, B: Reads: IsOption]: ResponseAs[Either[ResponseException[E, JsError], B]] =
    asJson[B].mapLeft { (l: ResponseException[String, JsError]) =>
      l match {
        case HttpError(e, code) =>
          deserializeJson[E].apply(e).fold(DeserializationException(e, _), HttpError(_, code))
        case de @ DeserializationException(_, _) => de
      }
    }.showAsJsonEither

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: Reads: IsOption, B: Reads: IsOption]: ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata(deserializeEitherWithErrorOrThrow(deserializeJson[E], deserializeJson[B]))
      .showAsJsonEitherOrFail

  // Note: None of the play-json utilities attempt to catch invalid
  // json, so Json.parse needs to be wrapped in Try
  def deserializeJson[B: Reads: IsOption]: String => Either[JsError, B] =
    JsonInput.sanitize[B].andThen { s =>
      Try(Json.parse(s)) match {
        case Failure(e: Exception) => Left(JsError(e.getMessage))
        case Failure(t: Throwable) => throw t
        case Success(json) =>
          Json.fromJson(json).asEither match {
            case Left(failures) => Left(JsError(failures))
            case Right(success) => Right(success)
          }
      }
    }
}
