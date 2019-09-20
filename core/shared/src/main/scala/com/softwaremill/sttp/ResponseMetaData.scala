package com.softwaremill.sttp

import com.softwaremill.sttp.model._
import scala.collection.immutable.Seq
import scala.util.Try

trait ResponseMetadata {
  def headers: Seq[(String, String)]
  def header(h: String): Option[String] = headers.find(_._1.equalsIgnoreCase(h)).map(_._2)
  def headers(h: String): Seq[String] = headers.filter(_._1.equalsIgnoreCase(h)).map(_._2)
  def contentType: Option[String] = header(HeaderNames.ContentType)
  def contentLength: Option[Long] = header(HeaderNames.ContentLength).flatMap(cl => Try(cl.toLong).toOption)

  def code: StatusCode
  def statusText: String
  def is200: Boolean = code == StatusCodes.Ok
  def isSuccess: Boolean = StatusCodes.isSuccess(code)
  def isRedirect: Boolean = StatusCodes.isRedirect(code)
  def isClientError: Boolean = StatusCodes.isClientError(code)
  def isServerError: Boolean = StatusCodes.isServerError(code)
}

object ResponseMetadata {
  def apply(h: Seq[(String, String)], c: StatusCode, st: String): ResponseMetadata = new ResponseMetadata {
    override def headers: Seq[(String, String)] = h
    override def code: StatusCode = c
    override def statusText: String = st
  }
}
