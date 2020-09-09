package sttp.client.playJson

import play.api.libs.json.{JsError, Json, Reads, Writes}
import sttp.client.internal.Utf8
import sttp.client.json._
import sttp.client.{IsOption, JsonInput, ResponseAs, _}
import sttp.model.MediaType

import scala.util.{Failure, Success, Try}

trait SttpPlayJsonApi {
  implicit val errorMessageForPlayError: ShowError[JsError] = new ShowError[JsError] {
    override def show(t: JsError): String = t.errors.mkString(",")
  }

  implicit def playJsonBodySerializer[B: Writes]: BodySerializer[B] =
    b => StringBody(Json.stringify(Json.toJson(b)), Utf8, MediaType.ApplicationJson)

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: Reads: IsOption]: ResponseAs[Either[ResponseException[String, JsError], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson[B])).showAsJson

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: Reads: IsOption]: ResponseAs[Either[DeserializationException[JsError], B], Any] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson[B])).showAsJsonAlways

  /**
    * Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: Reads: IsOption, B: Reads: IsOption]
      : ResponseAs[Either[ResponseException[E, JsError], B], Any] = {
    asJson[B].mapLeft { case HttpError(e, code) =>
      deserializeJson[E].apply(e).fold(DeserializationException(e, _), HttpError(_, code))
    }.showAsJsonEither
  }

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
