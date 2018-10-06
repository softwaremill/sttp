package com.softwaremill.sttp

import scala.util.{Try, Failure, Success}

import play.api.libs.json._
import com.softwaremill.sttp.internal.Utf8

package object playJson {
  implicit def playJsonBodySerializer[B : Writes]: BodySerializer[B] =
    b => StringBody(Json.stringify(Json.toJson(b)), Utf8, Some(MediaTypes.Json))

  // Note: None of the play-json utilities attempt to catch invalid
  // json, so Json.parse needs to be wrapped in Try
  def asJson[B : Reads]: ResponseAs[Either[DeserializationError[JsError], B], Nothing] =
    asString(Utf8).map { string =>
      val parsed: Either[JsError, B] = Try(Json.parse(string)) match {
        case Failure(t) => Left(JsError(t.getMessage))
        case Success(json) => Json.fromJson(json).asEither.left.map(JsError(_))
      }
      parsed.left.map(e => DeserializationError(string, e, Json.prettyPrint(JsError.toJson(e))))
    }
}
