package com.softwaremill.sttp

import com.softwaremill.sttp.model._
import io.circe.parser._
import io.circe.{Decoder, Encoder}

package object circe {
  private[sttp] val ApplicationJsonContentType = "application/json"

  implicit def circeBodySerializer[B](
      implicit encoder: Encoder[B]): BodySerializer[B] =
    b => StringBody(encoder(b).noSpaces, Utf8, Some(ApplicationJsonContentType))

  def asJson[B: Decoder]: ResponseAs[Either[io.circe.Error, B], Nothing] =
    asString(Utf8).map(decode[B])

}
