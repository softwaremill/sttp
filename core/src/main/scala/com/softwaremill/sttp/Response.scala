package com.softwaremill.sttp

import java.net.HttpCookie
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.{
  Calendar,
  GregorianCalendar,
  Locale,
  StringTokenizer,
  TimeZone
}

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.util.Try

/**
  * @param body `Right(T)`, if the request was successful (status code 2xx).
  *            The body is then handled as specified in the request.
  *            `Left(String)`, if the request wasn't successful (status code
  *            3xx, 4xx or 5xx). In this case, the response body is read into
  *            a `String`.
  * @param history If redirects are followed, and there were redirects,
  *                contains responses for the intermediate requests.
  *                The first response (oldest) comes first.
  */
case class Response[T](body: Either[String, T],
                       code: Int,
                       headers: Seq[(String, String)],
                       history: List[Response[Unit]]) {
  def is200: Boolean = code == 200
  def isSuccess: Boolean = codeIsSuccess(code)
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

  def cookies: Seq[Cookie] =
    headers(SetCookieHeader)
      .flatMap(h => HttpCookie.parse(h).asScala.map(hc => Cookie.apply(hc, h)))

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

case class Cookie(name: String,
                  value: String,
                  expires: Option[ZonedDateTime] = None,
                  maxAge: Option[Long] = None,
                  domain: Option[String] = None,
                  path: Option[String] = None,
                  secure: Boolean = false,
                  httpOnly: Boolean = false)

object Cookie {
  def apply(hc: HttpCookie, h: String): Cookie = {
    // HttpCookie.parse has special handling for the expires attribute and
    // turns it into max-age if the cookie contains an expires header;
    // hand-parsing in such case to preserve the values from the cookie
    val lch = h.toLowerCase
    val (expires, maxAge) = if (lch.contains("expires=")) {
      val tokenizer = new StringTokenizer(h, ";")
      var e: Option[ZonedDateTime] = None
      var ma: Option[Long] = None

      while (tokenizer.hasMoreTokens) {
        val t = tokenizer.nextToken()
        val nv = t.split("=", 2)
        if (nv(0).toLowerCase.contains("expires") && nv.length > 1) {
          e = expiryDate2ZonedDateTime(nv(1).trim())
        }
        if (nv(0).toLowerCase.contains("max-age") && nv.length > 1) {
          ma = Try(nv(1).toLong).toOption
        }
      }

      (e, ma)
    } else {
      (None, if (hc.getMaxAge == -1) None else Some(hc.getMaxAge))
    }

    Cookie(
      hc.getName,
      hc.getValue,
      expires,
      maxAge,
      Option(hc.getDomain),
      Option(hc.getPath),
      hc.getSecure,
      hc.isHttpOnly
    )
  }

  /**
    * Modified version of `HttpCookie.expiryDate2DeltaSeconds` to return a
    * `ZonedDateTime`, not a second-delta.
    */
  private def expiryDate2ZonedDateTime(
      dateString: String): Option[ZonedDateTime] = {
    val cal = new GregorianCalendar(Gmt)
    CookieDateFormats.foreach { format =>
      val df = new SimpleDateFormat(format, Locale.US)
      cal.set(1970, 0, 1, 0, 0, 0)
      df.setTimeZone(Gmt)
      df.setLenient(false)
      df.set2DigitYearStart(cal.getTime)
      try {
        cal.setTime(df.parse(dateString))
        if (!format.contains("yyyy")) {
          // 2-digit years following the standard set
          // out it rfc 6265
          var year = cal.get(Calendar.YEAR)
          year %= 100
          if (year < 70) year += 2000
          else year += 1900
          cal.set(Calendar.YEAR, year)
        }

        return Some(cal.toZonedDateTime)
      } catch {
        case e: Exception =>
        // Ignore, try the next date format
      }
    }

    None
  }
  private val Gmt = TimeZone.getTimeZone("GMT")
  private val CookieDateFormats = List(
    "EEE',' dd-MMM-yyyy HH:mm:ss 'GMT'",
    "EEE',' dd MMM yyyy HH:mm:ss 'GMT'",
    "EEE MMM dd yyyy HH:mm:ss 'GMT'Z",
    "EEE',' dd-MMM-yy HH:mm:ss 'GMT'",
    "EEE',' dd MMM yy HH:mm:ss 'GMT'",
    "EEE MMM dd yy HH:mm:ss 'GMT'Z"
  )
}
