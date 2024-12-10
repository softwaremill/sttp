package sttp.client4

import sttp.model.StatusCode

import scala.annotation.tailrec

/** Used to represent errors, that might occur when handling the response body. Either:
  *   - a [[HttpError]], when the response code is different than the expected one; desrialization is not attempted
  *   - a [[DeserializationException]], when there's an error during deserialization
  *
  * @tparam HE
  *   The type of the body to which the response is read, when the resposne code is different than the expected one
  * @tparam DE
  *   A deserialization-library-specific error type, describing the deserialization error in more detail
  */
sealed abstract class ResponseException[+HE, +DE](error: String) extends Exception(error)
case class HttpError[+HE](body: HE, statusCode: StatusCode)
    extends ResponseException[HE, Nothing](s"statusCode: $statusCode, response: $body")
case class DeserializationException[+DE: ShowError](body: String, error: DE)
    extends ResponseException[Nothing, DE](implicitly[ShowError[DE]].show(error))

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
