package sttp.model

import scala.collection.immutable.Seq
import scala.util.Try

trait Headers {
  def headers: Seq[Header]
  def header(h: String): Option[String] = headers.find(_.is(h)).map(_.value)
  def headers(h: String): Seq[String] = headers.filter(_.is(h)).map(_.value)

  def contentType: Option[String] = header(HeaderNames.ContentType)
  def contentLength: Option[Long] = header(HeaderNames.ContentLength).flatMap(cl => Try(cl.toLong).toOption)

  def cookies: Seq[CookieWithMeta] =
    headers(HeaderNames.SetCookie)
      .map(h => CookieWithMeta.parse(h).fold(e => throw new RuntimeException(e), identity[CookieWithMeta]))
}

object Headers {
  def apply(hs: Seq[Header]): Headers = new Headers {
    override def headers: Seq[Header] = hs
    override def toString = s"Headers(${hs.map(_.toString).mkString(",")})"
  }
}
