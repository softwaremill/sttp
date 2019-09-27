package sttp.client.sprayJson

import sttp.client._
import sttp.client.internal.Utf8
import sttp.model._
import spray.json._
import sttp.client.{IsOption, ResponseAs, ResponseError}

trait SttpSprayJsonApi {
  implicit def sprayBodySerializer[B: JsonWriter](implicit printer: JsonPrinter = CompactPrinter): BodySerializer[B] =
    b => StringBody(printer(b.toJson), Utf8, Some(MediaTypes.Json))

  def asJson[B: JsonReader: IsOption]: ResponseAs[Either[ResponseError[Exception], B], Nothing] =
    ResponseAs.deserializeFromStringCatchingExceptions(asString, deserializeJson[B])

  def deserializeJson[B: JsonReader: IsOption]: String => B =
    JsonInput.sanitize[B].andThen((_: String).parseJson.convertTo[B])
}
