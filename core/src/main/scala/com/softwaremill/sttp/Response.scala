package com.softwaremill.sttp

import scala.collection.immutable.Seq
import scala.util.Try

case class Response[T](body: T, code: Int, headers: Seq[(String, String)]) {
  def is200: Boolean = code == 200
  def isSuccess: Boolean = code >= 200 && code < 300
  def isRedirect: Boolean = code >= 300 && code < 400
  def isClientError: Boolean = code >= 400 && code < 500
  def isServerError: Boolean = code >= 500 && code < 600

  def header(h: String): Option[String] =
    headers.find(_._1.equalsIgnoreCase(h)).map(_._2)
  def headers(h: String): Seq[String] =
    headers.filter(_._1.equalsIgnoreCase(h)).map(_._2)

  def contentType: Option[String] = header(ContentTypeHeader)
  def contentLength: Option[Long] =
    header(ContentLengthHeader).flatMap(cl => Try(cl.toLong).toOption)
}
