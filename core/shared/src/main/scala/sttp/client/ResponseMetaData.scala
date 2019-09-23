package sttp.client

import sttp.client.model._
import sttp.client.model.HeaderNames

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
  def is200: Boolean = code == StatusCode.Ok
  def isSuccess: Boolean = code.isSuccess
  def isRedirect: Boolean = code.isRedirect
  def isClientError: Boolean = code.isClientError
  def isServerError: Boolean = code.isServerError
}

object ResponseMetadata {
  def apply(h: Seq[(String, String)], c: StatusCode, st: String): ResponseMetadata = new ResponseMetadata {
    override def headers: Seq[(String, String)] = h
    override def code: StatusCode = c
    override def statusText: String = st
  }
}
