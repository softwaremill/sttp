package com.softwaremill.sttp.json4s

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal.Utf8
import org.json4s.{DefaultFormats, Formats, Serialization}
import com.softwaremill.sttp.model._

trait SttpJson4sApi {
  implicit def json4sBodySerializer[B <: AnyRef](
      implicit formats: Formats = DefaultFormats,
      serialization: Serialization
  ): BodySerializer[B] =
    b => StringBody(serialization.write(b), Utf8, Some(MediaTypes.Json))

  def asJson[B: Manifest](
      implicit formats: Formats = DefaultFormats,
      serialization: Serialization
  ): ResponseAs[Either[ResponseError[Exception], B], Nothing] =
    ResponseAs.deserializeFromStringCatchingExceptions(deserializeJson[B])

  def deserializeJson[B: Manifest](
      implicit formats: Formats = DefaultFormats,
      serialization: Serialization
  ): String => B =
    JsonInput.sanitize[B].andThen(serialization.read[B])
}
