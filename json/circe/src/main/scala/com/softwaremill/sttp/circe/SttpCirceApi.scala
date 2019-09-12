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

  def asJson[B: Decoder: IsOption]: ResponseAs[Either[ResponseError[io.circe.Error], B], Nothing] =
    asString(Utf8)
      .mapRight(JsonInput.sanitize[B])
      .map {
        case Left(s) => Left(HttpError(s))
        case Right(s) =>
          decode[B](s) match {
            case Left(e)  => Left(DeserializationError(s, e, Show[io.circe.Error].show(e)))
            case Right(b) => Right(b)
          }
      }
}
