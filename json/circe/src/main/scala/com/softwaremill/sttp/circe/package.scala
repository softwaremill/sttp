package com.softwaremill.sttp

import io.circe.parser._
import io.circe.{Decoder, Encoder}

package object circe {

  implicit def circeBodySerializer[B](implicit encoder: Encoder[B]): BodySerializer[B] =
    b => StringBody(encoder(b).noSpaces, Utf8, Some(MediaTypes.Json))

  def asJson[B: Decoder]: ResponseAs[Either[io.circe.Error, B], Nothing] =
    asString(Utf8).map(decode[B])

}
