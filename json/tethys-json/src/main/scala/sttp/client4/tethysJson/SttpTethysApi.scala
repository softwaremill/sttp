package sttp.client4.tethysJson

import sttp.client4._
import sttp.client4.internal.Utf8
import sttp.client4.json.RichResponseAs
import sttp.model.MediaType
import tethys._
import tethys.readers.ReaderError
import tethys.readers.tokens.TokenIteratorProducer
import tethys.writers.tokens.TokenWriterProducer
import sttp.client4.ResponseAs.deserializeEitherWithErrorOrThrow

trait SttpTethysApi {

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B](b: B)(implicit
      jsonWriter: JsonWriter[B],
      tokenWriterProducer: TokenWriterProducer
  ): StringBody = StringBody(b.asJson, Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): ResponseAs[Either[ResponseException[String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): ResponseAs[Either[DeserializationException, B]] =
    asStringAlways.mapWithMetadata(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: JsonReader: IsOption, B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata(deserializeEitherWithErrorOrThrow(deserializeJson[E], deserializeJson[B]))
      .showAsJsonEitherOrFail

  private def deserializeJson[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): String => Either[ReaderError, B] =
    JsonInput.sanitize[B].andThen(_.jsonAs[B])
}
