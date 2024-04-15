package sttp.client4.tethysJson

import sttp.client4._
import sttp.client4.internal.Utf8
import sttp.client4.json.RichResponseAs
import sttp.model.MediaType
import tethys._
import tethys.readers.ReaderError
import tethys.readers.tokens.TokenIteratorProducer
import tethys.writers.tokens.TokenWriterProducer

trait SttpTethysApi {

  implicit def tethysBodySerializer[B](implicit
      jsonWriter: JsonWriter[B],
      tokenWriterProducer: TokenWriterProducer
  ): BodySerializer[B] =
    b => StringBody(b.asJson, Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(HttpError(String))` if the response code was other than 2xx (deserialization is not attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): ResponseAs[Either[ResponseException[String, ReaderError], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): ResponseAs[Either[DeserializationException[ReaderError], B]] =
    asStringAlways.map(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  private def deserializeJson[B: JsonReader: IsOption](implicit
      producer: TokenIteratorProducer
  ): String => Either[ReaderError, B] =
    JsonInput.sanitize[B].andThen(_.jsonAs[B])
}
