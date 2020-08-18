package sttp.client.playJson

import play.api.libs.json.{JsError, Json, Reads, Writes}
import sttp.client.internal.Utf8
import sttp.client.{IsOption, JsonInput, ResponseAs, _}
import sttp.model.MediaType

import scala.util.{Failure, Success, Try}

trait SttpPlayJsonApi {
  implicit val errorMessageForPlayError: ShowError[JsError] = new ShowError[JsError] {
    override def show(t: JsError): String = t.errors.mkString(",")
  }

  implicit def playJsonBodySerializer[B: Writes]: BodySerializer[B] =
    b => StringBody(Json.stringify(Json.toJson(b)), Utf8, Some(MediaType.ApplicationJson))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: Reads: IsOption]: ResponseAs[Either[ResponseError[JsError], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: Reads: IsOption]: ResponseAs[Either[DeserializationError[JsError], B], Any] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns the parse
    * result, or throws an exception is there's an error during deserialization
    */
  def asJsonAlwaysUnsafe[B: Reads: IsOption]: ResponseAs[B, Any] =
    asStringAlways.map(ResponseAs.deserializeOrThrow(deserializeJson))

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
