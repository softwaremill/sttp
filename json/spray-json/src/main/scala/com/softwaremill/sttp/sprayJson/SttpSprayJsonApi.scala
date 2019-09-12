package com.softwaremill.sttp.sprayJson

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal.Utf8
import spray.json._

trait SttpSprayJsonApi {
  implicit def sprayBodySerializer[B: JsonWriter](implicit printer: JsonPrinter = CompactPrinter): BodySerializer[B] =
    b => StringBody(printer(b.toJson), Utf8, Some(MediaTypes.Json))

  def asJson[B: JsonReader: IsOption]: ResponseAs[Either[ResponseError[Exception], B], Nothing] =
    ResponseAs.deserializeCatchingExceptions(
      asString(Utf8).mapRight(JsonInput.sanitize[B]),
      (_: String).parseJson.convertTo[B]
    )
}
