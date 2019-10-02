package sttp.client.sprayJson

import sttp.client._
import sttp.client.internal.Utf8
import sttp.model._
import spray.json._
import sttp.client.{IsOption, ResponseAs, ResponseError}

trait SttpSprayJsonApi {
  implicit def sprayBodySerializer[B: JsonWriter](implicit printer: JsonPrinter = CompactPrinter): BodySerializer[B] =
    b => StringBody(printer(b.toJson), Utf8, Some(MediaTypes.Json))

  /**
    * If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJson[B: JsonReader: IsOption]: ResponseAs[Either[ResponseError[Exception], B], Nothing] =
    asString.map(ResponseAs.deserializeRightCatchingExceptions(deserializeJson[B]))

  /**
    * Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationError)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonReader: IsOption]: ResponseAs[Either[DeserializationError[Exception], B], Nothing] =
    asStringAlways.map(ResponseAs.deserializeCatchingExceptions(deserializeJson[B]))

  def deserializeJson[B: JsonReader: IsOption]: String => B =
    JsonInput.sanitize[B].andThen((_: String).parseJson.convertTo[B])
}
