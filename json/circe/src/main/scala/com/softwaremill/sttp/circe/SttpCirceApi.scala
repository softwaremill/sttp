package com.softwaremill.sttp.circe

import cats.Show
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

  def asJson[B: Decoder: IsOption]: ResponseAs[Either[DeserializationError[io.circe.Error], B], Nothing] =
    asString(Utf8)
      .map(JsonInput.sanitize[B])
      .map(s => decode[B](s).left.map(e => DeserializationError(s, e, Show[io.circe.Error].show(e))))
}
