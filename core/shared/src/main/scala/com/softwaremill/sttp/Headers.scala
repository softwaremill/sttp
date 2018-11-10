package com.softwaremill.sttp

import scala.collection.immutable.Seq
import scala.util.Try

trait Headers {
  def headers: Seq[(String, String)]
  def header(h: String): Option[String] = headers.find(_._1.equalsIgnoreCase(h)).map(_._2)
  def headers(h: String): Seq[String] = headers.filter(_._1.equalsIgnoreCase(h)).map(_._2)
  def contentType: Option[String] = header(HeaderNames.ContentType)
  def contentLength: Option[Long] = header(HeaderNames.ContentLength).flatMap(cl => Try(cl.toLong).toOption)
}

object Headers {
  def apply(h: Seq[(String, String)]): Headers = new Headers {
    override def headers: Seq[(String, String)] = h
  }
}