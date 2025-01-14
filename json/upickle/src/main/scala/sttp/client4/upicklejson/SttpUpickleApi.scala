package sttp.client4.upicklejson

import sttp.client4._
import sttp.client4.internal.Utf8
import sttp.model.MediaType
import sttp.client4.json._
import sttp.client4.ResponseAs.deserializeEitherWithErrorOrThrow
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.ResponseException.UnexpectedStatusCode

trait SttpUpickleApi {
  val upickleApi: upickle.Api

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B](b: B)(implicit encoder: upickleApi.Writer[B]): StringBody =
    StringBody(upickleApi.write(b), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(UnexpectedStatusCode(String))` if the response code was other than 2xx (deserialization is not
    *     attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: upickleApi.Reader: IsOption]: ResponseAs[Either[ResponseException[String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: upickleApi.Reader: IsOption]: ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: upickleApi.Reader: IsOption]: ResponseAs[Either[DeserializationException, B]] =
    asStringAlways.mapWithMetadata(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(UnexpectedStatusCode(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: upickleApi.Reader: IsOption, B: upickleApi.Reader: IsOption]
      : ResponseAs[Either[ResponseException[E], B]] =
    asJson[B].mapLeft { (l: ResponseException[String]) =>
      l match {
        case UnexpectedStatusCode(e, meta) =>
          deserializeJson[E].apply(e).fold(DeserializationException(e, _, meta), UnexpectedStatusCode(_, meta))
        case de: DeserializationException => de
      }
    }.showAsJsonEither

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: upickleApi.Reader: IsOption, B: upickleApi.Reader: IsOption]: ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata(deserializeEitherWithErrorOrThrow(deserializeJson[E], deserializeJson[B]))
      .showAsJsonEitherOrFail

  def deserializeJson[B: upickleApi.Reader: IsOption]: String => Either[Exception, B] = { (s: String) =>
    try
      Right(upickleApi.read[B](JsonInput.sanitize[B].apply(s)))
    catch {
      case e: Exception => Left(e)
      case t: Throwable =>
        // in ScalaJS, ArrayIndexOutOfBoundsException exceptions are wrapped in org.scalajs.linker.runtime.UndefinedBehaviorError
        t.getCause match {
          case e: ArrayIndexOutOfBoundsException => Left(e)
          case _                                 => throw t
        }
    }
  }
}
