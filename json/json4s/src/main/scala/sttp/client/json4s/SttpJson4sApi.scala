package sttp.client.json4s

import sttp.client._
import sttp.client.internal.Utf8
import org.json4s.{DefaultFormats, Formats, Serialization}
import sttp.model._
import sttp.client.{ResponseAs, ResponseError}

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
