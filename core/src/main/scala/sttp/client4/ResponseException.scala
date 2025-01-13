package sttp.client4

import sttp.model.StatusCode

import scala.annotation.tailrec

/** Used to represent errors, that might occur when handling the response body. Typically, this type is used as the
  * left-side of a top-level either (where the right-side represents a successful request and deserialization).
  *
  * A response exception can itself either be one of two cases:
  *   - a [[HttpError]], when the response code is other than 2xx (or whatever is considered "success" by the response
  *     handling description); the body is deserialized to `HE`
  *   - a [[DeserializationException]], when there's an error during deserialization (this might include both
  *     deserialization exceptions of the success and error branches)
  *
  * When thrown/returned when sending a request, will be additionally wrapped with a
  * [[SttpClientException.ResponseHandlingException]].
  *
  * @tparam HE
  *   The type of the body to which the response is deserialized, when the response code is different than success
  *   (typically 2xx status code).
  * @tparam DE
  *   A deserialization-library-specific error type, describing the deserialization error in more detail.
  */
sealed abstract class ResponseException[+HE, +DE](error: String, cause: Option[Throwable])
    extends Exception(error, cause.orNull)

/** Represents an http error, where the response was received successfully, but the status code is other than the
  * expected one (typically other than 2xx).
  *
  * @tparam HE
  *   The type of the body to which the error response is deserialized.
  */
case class HttpError[+HE](body: HE, statusCode: StatusCode)
    extends ResponseException[HE, Nothing](s"statusCode: $statusCode, response: $body", None)

/** Represents an error that occurred during deserialization of `body`.
  *
  * @tparam DE
  *   A deserialization-library-specific error type, describing the deserialization error in more detail.
  */
case class DeserializationException[+DE: ShowError](body: String, error: DE)
    extends ResponseException[Nothing, DE](
      implicitly[ShowError[DE]].show(error),
      if (error.isInstanceOf[Throwable]) Some(error.asInstanceOf[Throwable]) else None
    )

object HttpError {
  @tailrec def find(exception: Throwable): Option[HttpError[_]] =
    Option(exception) match {
      case Some(error: HttpError[_]) => Some(error)
      case Some(_)                   => find(exception.getCause)
      case None                      => Option.empty
    }
}

trait ShowError[-T] {
  def show(t: T): String
}

object ShowError {
  implicit val showErrorMessageFromException: ShowError[Exception] = new ShowError[Exception] {
    override def show(t: Exception): String = t.getMessage
  }
}
