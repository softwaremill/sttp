package com.softwaremill.sttp.sprayJson

import com.softwaremill.sttp.{BodySerializer, IsOption, MediaTypes, ResponseAs, StringBody, asString, JsonInput}
import com.softwaremill.sttp.internal.Utf8
import spray.json._

trait SttpSprayJsonApi {
  implicit def sprayBodySerializer[B: JsonWriter](implicit printer: JsonPrinter = CompactPrinter): BodySerializer[B] =
    b => StringBody(printer(b.toJson), Utf8, Some(MediaTypes.Json))

  def asJson[B: JsonReader: IsOption]: ResponseAs[B, Nothing] =
    asString(Utf8)
      .map(JsonInput.sanitize[B])
      .map(_.parseJson.convertTo[B])

}
