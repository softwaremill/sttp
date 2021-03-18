package sttp.client3.upickle

import upickle.default.{Reader, Writer, read, write}
import sttp.client3._
import sttp.client3.internal.Utf8
import sttp.model.MediaType
import sttp.client3.json._

trait SttpUpickleApi {

  implicit def upickleBodySerializer[B](implicit encoder: Writer[B]): BodySerializer[B] =
    b => StringBody(write(b), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: Reader: IsOption]: ResponseAs[Either[ResponseException[String, Exception], B], Any] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    * - `Right(b)` if the parsing was successful
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: Reader: IsOption]: ResponseAs[Either[DeserializationException[Exception], B], Any] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the
    * status code. Returns:
    * - `Right(B)` if the response was 2xx and parsing was successful
    * - `Left(HttpError(E))` if the response was other than 2xx and parsing was successful
    * - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: Reader: IsOption, B: Reader: IsOption]
      : ResponseAs[Either[ResponseException[E, Exception], B], Any] = {
    asJson[B].mapLeft {
      case HttpError(e, code)                  => deserializeJson[E].apply(e).fold(DeserializationException(e, _), HttpError(_, code))
      case de @ DeserializationException(_, _) => de
    }.showAsJsonEither
  }

  def deserializeJson[B: Reader: IsOption]: String => Either[Exception, B] = { s: String =>
    try {
      Right(read[B](JsonInput.sanitize[B].apply(s)))
    } catch {
      case e: Exception => Left(e)
      case t: Throwable => // in ScalaJS, exceptions are wrapped in org.scalajs.linker.runtime.UndefinedBehaviorError
        t.getCause match {
          case e: Exception => Left(e)
          case _            => throw t
        }
    }
  }
}
