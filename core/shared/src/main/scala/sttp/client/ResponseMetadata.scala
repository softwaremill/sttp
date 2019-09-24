package sttp.client

import sttp.model._
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.collection.immutable.Seq
import scala.util.Try

trait ResponseMetadata {
  def headers: Seq[Header]
  def header(h: String): Option[String] = headers.find(_.name.equalsIgnoreCase(h)).map(_.value)
  def headers(h: String): Seq[String] = headers.filter(_.name.equalsIgnoreCase(h)).map(_.value)
  def contentType: Option[String] = header(HeaderNames.ContentType)
  def contentLength: Option[Long] = header(HeaderNames.ContentLength).flatMap(cl => Try(cl.toLong).toOption)

  def cookies: Seq[Cookie] =
    headers(HeaderNames.SetCookie)
      .map(h => Cookie.parseHeaderValue(h).fold(e => throw new RuntimeException(e), identity[Cookie]))

  def code: StatusCode
  def statusText: String
  def is200: Boolean = code == StatusCode.Ok
  def isSuccess: Boolean = code.isSuccess
  def isRedirect: Boolean = code.isRedirect
  def isClientError: Boolean = code.isClientError
  def isServerError: Boolean = code.isServerError
}

object ResponseMetadata {
  def apply(h: Seq[Header], c: StatusCode, st: String): ResponseMetadata = new ResponseMetadata {
    override def headers: Seq[Header] = h
    override def code: StatusCode = c
    override def statusText: String = st
  }
}
