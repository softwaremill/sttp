package com.softwaremill.sttp

import com.softwaremill.sttp.model.{
  RequestBody,
  ResponseAs,
  SerializableBody,
  StringBody
}
import io.circe.parser._
import io.circe.{Decoder, Encoder}

import scala.language.higherKinds

package object circe {
  private[sttp] val ApplicationJsonContentType = "application/json"

  private def serializeBody[B](body: B)(
      implicit encoder: Encoder[B]): RequestBody[Nothing] =
    SerializableBody((b: B) => StringBody(encoder(b).noSpaces, Utf8), body)

  implicit final class CirceRequestTOps[U[_], T, +S](val req: RequestT[U, T, S])
      extends AnyVal {
    def jsonBody[B: Encoder](body: B): RequestT[U, T, S] =
      req
        .setContentTypeIfMissing(ApplicationJsonContentType)
        .copy(body = serializeBody(body))
  }

  def asJson[B: Decoder]: ResponseAs[Either[io.circe.Error, B], Nothing] =
    asString(Utf8).map(decode[B])

}
