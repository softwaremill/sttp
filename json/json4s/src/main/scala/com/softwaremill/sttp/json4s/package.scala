package com.softwaremill.sttp

import org.json4s._
import org.json4s.native.Serialization.{read, write}

package object json4s {
  implicit def json4sBodySerializer[B <: AnyRef](implicit formats: Formats = DefaultFormats): BodySerializer[B] =
    b => StringBody(write(b), Utf8, Some(MediaTypes.Json))

  def asJson[B: Manifest](implicit formats: Formats = DefaultFormats): ResponseAs[B, Nothing] =
    asString(Utf8).map(s => read[B](s))
}
