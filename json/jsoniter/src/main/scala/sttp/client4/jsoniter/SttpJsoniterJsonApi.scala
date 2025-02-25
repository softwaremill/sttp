package sttp.client4.jsoniter

import sttp.client4.IsOption
import sttp.client4.JsonInput
import sttp.client4.ResponseAs
import sttp.client4.ResponseException
import sttp.client4.StringBody
import sttp.client4.asString
import sttp.client4.asStringAlways
import sttp.client4.internal.Utf8
import sttp.client4.json.RichResponseAs
import sttp.model.MediaType
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.ResponseException.UnexpectedStatusCode

trait SttpJsoniterJsonApi {
  import com.github.plokhotnyuk.jsoniter_scala.core._

  /** Serialize the given value as JSON, to be used as a request's body using [[sttp.client4.Request.body]]. */
  def asJson[B](b: B)(implicit encoder: JsonValueCodec[B]): StringBody =
    StringBody(writeToString(b), Utf8, MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(UnexpectedStatusCode(String))` if the response code was other than 2xx (deserialization is not
    *     attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonValueCodec: IsOption]: ResponseAs[Either[ResponseException[String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson[B])).showAsJson

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: JsonValueCodec: IsOption]: ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonValueCodec: IsOption]: ResponseAs[Either[DeserializationException, B]] =
    asStringAlways.mapWithMetadata(ResponseAs.deserializeWithError(deserializeJson[B])).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status code.
    * Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(UnexpectedStatusCode(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[
      E: JsonValueCodec: IsOption,
      B: JsonValueCodec: IsOption
  ]: ResponseAs[Either[ResponseException[E], B]] =
    asJson[B].mapLeft { (l: ResponseException[String]) =>
      l match {
        case de @ DeserializationException(_, _, _) => de
        case UnexpectedStatusCode(e, meta) =>
          deserializeJson[E].apply(e).fold(DeserializationException(e, _, meta), UnexpectedStatusCode(_, meta))
      }
    }.showAsJsonEither

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: JsonValueCodec: IsOption, B: JsonValueCodec: IsOption]: ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata((s, meta) =>
        ResponseAs
          .deserializeEitherWithErrorOrThrow(
            s => deserializeJson[E].apply(s),
            s => deserializeJson[B].apply(s)
          )
          .apply(s, meta)
      )
      .showAsJsonEitherOrFail

  def deserializeJson[B: JsonValueCodec: IsOption]: String => Either[JsonReaderException, B] = { (s: String) =>
    try Right(readFromString[B](JsonInput.sanitize[B].apply(s)))
    catch {
      case de: JsonReaderException => Left(de)
    }
  }

  implicit def optionDecoder[T: JsonValueCodec]: JsonValueCodec[Option[T]] = new JsonValueCodec[Option[T]] {
    private val codec = implicitly[JsonValueCodec[T]]
    override def decodeValue(in: JsonReader, default: Option[T]): Option[T] =
      if (
        in.isNextToken('n'.toByte)
        && in.isNextToken('u'.toByte)
        && in.isNextToken('l'.toByte)
        && in.isNextToken('l'.toByte)
      ) {
        None
      } else {
        in.rollbackToken()
        Some(codec.decodeValue(in, codec.nullValue))
      }

    override def encodeValue(x: Option[T], out: JsonWriter): Unit = x.foreach(codec.encodeValue(_, out))

    override def nullValue: Option[T] = null
  }
}
