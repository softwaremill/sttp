package com.softwaremill.sttp.playJson

import com.softwaremill.sttp._
import com.softwaremill.sttp.internal.Utf8
import play.api.libs.json.{JsError, Json, Reads, Writes}

import scala.util.{Failure, Success, Try}

trait SttpPlayJsonApi {
  implicit def playJsonBodySerializer[B: Writes]: BodySerializer[B] =
    b => StringBody(Json.stringify(Json.toJson(b)), Utf8, Some(MediaTypes.Json))

  // Note: None of the play-json utilities attempt to catch invalid
  // json, so Json.parse needs to be wrapped in Try
  def asJson[B: Reads: IsOption]: ResponseAs[Either[ResponseError[JsError], B], Nothing] =
    ResponseAs.deserializeFromString(deserializeJson[B])

  def deserializeJson[B: Reads: IsOption]: String => Either[JsError, B] = JsonInput.sanitize[B].andThen { s =>
    Try(Json.parse(s)) match {
      case Failure(e: Exception) => Left(JsError(e.getMessage))
      case Failure(t: Throwable) => throw t
      case Success(json) =>
        Json.fromJson(json).asEither match {
          case Left(failures) => Left(JsError(failures))
          case Right(success) => Right(success)
        }
    }
  }
}
