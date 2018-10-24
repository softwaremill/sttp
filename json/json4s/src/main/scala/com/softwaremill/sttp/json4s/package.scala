package com.softwaremill.sttp

import com.softwaremill.sttp.internal._

import org.json4s._
import org.json4s.Serialization

package object json4s {
  implicit def json4sBodySerializer[B <: AnyRef](implicit formats: Formats = DefaultFormats,
                                                 serialization: Serialization): BodySerializer[B] =
    b => StringBody(serialization.write(b), Utf8, Some(MediaTypes.Json))

  def asJson[B: Manifest](implicit formats: Formats = DefaultFormats,
                          serialization: Serialization): ResponseAs[B, Nothing] =
    asString(Utf8).map(s => serialization.read[B](s))
}
