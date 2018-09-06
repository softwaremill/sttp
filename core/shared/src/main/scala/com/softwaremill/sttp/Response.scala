package com.softwaremill.sttp

import com.softwaremill.sttp.internal._

import scala.collection.immutable.Seq
import scala.util.Try

/**
  * @param rawErrorBody `Right(T)`, if the request was successful (status code 2xx).
  *            The body is then handled as specified in the request.
  *            `Left(Array[Byte])`, if the request wasn't successful (status code
  *            3xx, 4xx or 5xx).
  * @param history If redirects are followed, and there were redirects,
  *                contains responses for the intermediate requests.
  *                The first response (oldest) comes first.
  */
case class Response[T](rawErrorBody: Either[Array[Byte], T],
                       code: StatusCode,
                       statusText: String,
                       headers: Seq[(String, String)],
                       history: List[Response[Unit]])
    extends ResponseExtensions[T] {
  def is200: Boolean = code == StatusCodes.Ok
  def isSuccess: Boolean = StatusCodes.isSuccess(code)
  def isRedirect: Boolean = StatusCodes.isRedirect(code)
  def isClientError: Boolean = StatusCodes.isClientError(code)
  def isServerError: Boolean = StatusCodes.isServerError(code)

  def header(h: String): Option[String] =
    headers.find(_._1.equalsIgnoreCase(h)).map(_._2)
  def headers(h: String): Seq[String] =
    headers.filter(_._1.equalsIgnoreCase(h)).map(_._2)

  lazy val body: Either[String, T] = rawErrorBody match {
    case Left(bytes) =>
      val charset = contentType
        .flatMap(encodingFromContentType)
        .getOrElse(Utf8)
      Left(new String(bytes, charset))

    case Right(r) => Right(r)
  }

  def contentType: Option[String] = header(HeaderNames.ContentType)
  def contentLength: Option[Long] =
    header(HeaderNames.ContentLength).flatMap(cl => Try(cl.toLong).toOption)

  /**
    * Get the body of the response. If the status code wasn't 2xx (and there's
    * no body to return), an exception is thrown, containing the status code
    * and the response from the server.
    */
  def unsafeBody: T = body match {
    case Left(v)  => throw new NoSuchElementException(s"Status code $code: $v")
    case Right(v) => v
  }

  override def toString: String = {
    // trying to include the string representation of the error, if possible
    val b = Try(body).getOrElse(rawErrorBody.toString)
    s"Response($b,$code,$statusText,$headers,$history)"
  }
}

object Response {
  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[com.softwaremill.sttp.testing.SttpBackendStub]] and partial matchers.
    */
  def apply[T](body: Either[String, T], code: StatusCode, statusText: String): Response[T] =
    Response(body.left.map(_.getBytes(Utf8)), code, statusText, Nil, Nil)

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[com.softwaremill.sttp.testing.SttpBackendStub]] and partial matchers.
    */
  def ok[T](body: T): Response[T] = apply(Right(body), 200, "OK")

  /**
    * Convenience method to create a Response instance, mainly useful in tests using
    * [[com.softwaremill.sttp.testing.SttpBackendStub]] and partial matchers.
    */
  def error(body: String, code: StatusCode, statusText: String = "") = apply(Left(body), code, statusText)
}
