package com.softwaremill.sttp.sprayJson

import com.softwaremill.sttp.{BodySerializer, MediaTypes, ResponseAs, StringBody, asString}
import com.softwaremill.sttp.internal.Utf8
import spray.json._

trait SttpSprayJsonApi {
  implicit def sprayBodySerializer[B: JsonWriter](implicit printer: JsonPrinter = CompactPrinter): BodySerializer[B] =
    b => StringBody(printer(b.toJson), Utf8, Some(MediaTypes.Json))

  def asJson[B: JsonReader]: ResponseAs[B, Nothing] =
    asString(Utf8).map(_.parseJson.convertTo[B])
}
