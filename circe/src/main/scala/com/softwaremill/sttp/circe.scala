package com.softwaremill.sttp

import com.softwaremill.sttp.model.{ResponseAs, StringBody}
import io.circe.parser._
import io.circe.{Decoder, Encoder}

import scala.language.higherKinds

package object circe {
  private[sttp] val ApplicationJsonContentType = "application/json"

  implicit def circeBodySerializer[B: Encoder]: BodySerializer[B] =
    BodySerializer.instance(
      body => StringBody(Encoder[B].apply(body).noSpaces, Utf8),
      ApplicationJsonContentType)

  def asJson[B: Decoder]: ResponseAs[Either[io.circe.Error, B], Nothing] =
    asString(Utf8).map(decode[B])

}
