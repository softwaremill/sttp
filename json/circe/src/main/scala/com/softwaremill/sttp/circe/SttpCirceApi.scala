package com.softwaremill.sttp.circe

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal.Utf8
import io.circe.{Decoder, Encoder, Printer}
import io.circe.parser.decode

trait SttpCirceApi {
  implicit def circeBodySerializer[B](
      implicit encoder: Encoder[B],
      printer: Printer = Printer.noSpaces
  ): BodySerializer[B] =
    b => StringBody(encoder(b).pretty(printer), Utf8, Some(MediaTypes.Json))

  def asJson[B: Decoder: IsOption]: ResponseAs[Either[ResponseError[io.circe.Error], B], Nothing] =
    ResponseAs.deserializeFromString(deserializeJson)

  def deserializeJson[B: Decoder: IsOption]: String => Either[io.circe.Error, B] =
    JsonInput.sanitize[B].andThen(decode[B])
}
