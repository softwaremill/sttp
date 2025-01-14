package sttp.client4

import scala.annotation.tailrec
import sttp.model.ResponseMetadata

/** Used to represent errors, that might occur when handling the response body. Typically, this type is used as the
  * left-side of a top-level either (where the right-side represents a successful request and deserialization),
  *
  * A response exception can itself be one of two cases:
  *   - a [[HttpError]], when the response code is other than 2xx (or whatever is considered "success" by the response
  *     handling description); the body is deserialized to `HE`
  *   - a [[DeserializationException]], when there's an error during deserialization (this might include both
  *     deserialization exceptions of the success and error branches)
  *
  * When thrown/returned when sending a request (e.g. in `...OrFailed` response handling descriptions), will be
  * additionally wrapped with a [[SttpClientException.ResponseHandlingException]].
  *
  * @tparam HE
  *   The type of the body to which the response is deserialized, when the response code is different than success
  *   (typically 2xx status code).
  */
sealed abstract class ResponseException[+HE](
    error: String,
    cause: Option[Throwable],
    val response: ResponseMetadata
) extends Exception(error, cause.orNull)

/** Represents an http error, where the response was received successfully, but the status code is other than the
  * expected one (typically other than 2xx).
  *
  * @tparam HE
  *   The type of the body to which the error response is deserialized.
  */
case class HttpError[+HE](body: HE, override val response: ResponseMetadata)
    extends ResponseException[HE](s"statusCode: ${response.code}, response: $body", None, response)

/** Represents an error that occurred during deserialization of `body`. */
case class DeserializationException(body: String, cause: Exception, override val response: ResponseMetadata)
    extends ResponseException[Nothing](
      cause.getMessage(),
      Some(cause),
      response
    )

object ResponseException {
  @tailrec def find(exception: Throwable): Option[ResponseException[_]] =
    Option(exception) match {
      case Some(e: ResponseException[_]) => Some(e)
      case Some(_)                       => find(exception.getCause)
      case None                          => None
    }
}
