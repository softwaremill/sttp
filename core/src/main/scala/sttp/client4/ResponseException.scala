package sttp.client4

import sttp.model.StatusCode

import scala.annotation.tailrec

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
