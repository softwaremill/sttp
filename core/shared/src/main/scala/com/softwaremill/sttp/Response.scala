package com.softwaremill.sttp

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
                       code: Int,
                       statusText: String,
                       headers: Seq[(String, String)],
                       history: List[Response[Unit]]) extends ResponseExtensions[T] {
  def is200: Boolean = code == 200
  def isSuccess: Boolean = codeIsSuccess(code)
  def isRedirect: Boolean = code >= 300 && code < 400
  def isClientError: Boolean = code >= 400 && code < 500
  def isServerError: Boolean = code >= 500 && code < 600

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

  def contentType: Option[String] = header(ContentTypeHeader)
  def contentLength: Option[Long] =
    header(ContentLengthHeader).flatMap(cl => Try(cl.toLong).toOption)

  /**
    * Get the body of the response. If the status code wasn't 2xx (and there's
    * no body to return), an exception is thrown, containing the status code
    * and the response from the server.
    */
  def unsafeBody: T = body match {
    case Left(v)  => throw new NoSuchElementException(s"Status code $code: $v")
    case Right(v) => v
  }
}
