package sttp.model

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import scala.util.{Failure, Success, Try}

case class Cookie(
    pair: CookiePair,
    expires: Option[Instant],
    maxAge: Option[Long],
    domain: Option[String],
    path: Option[String],
    secure: Boolean,
    httpOnly: Boolean
) {
  def name: String = pair.name
  def value: String = pair.value

  def asHeaderValue: String = {
    val components = List(
      Some(s"$name=$value"),
      expires.map(e => s"Expires=${DateTimeFormatter.RFC_1123_DATE_TIME.format(e.atZone(ZoneId.of("GMT")))}"),
      maxAge.map(a => s"Max-Age=$a"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=$p"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None
    )

    components.flatten.mkString("; ")
  }
}

object Cookie {
  def apply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false
  ): Cookie = Cookie(CookiePair(name, value), expires, maxAge, domain, path, secure, httpOnly)

  // https://tools.ietf.org/html/rfc6265#section-4.1.1
  def parseHeaderValue(s: String): Either[String, Cookie] = {
    def splitkv(kv: String): (String, Option[String]) = kv.split("=", 2).map(_.trim) match {
      case Array(v1)     => (v1, None)
      case Array(v1, v2) => (v1, Some(v2))
    }

    val components = s.split(";").map(_.trim)
    val (first, other) = (components.head, components.tail)
    val (name, value) = splitkv(first)
    var result: Either[String, Cookie] = Right(Cookie(name, value.getOrElse("")))
    other.map(splitkv).foreach {
      case (k, Some(v)) if k.equalsIgnoreCase("expires") =>
        Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(v))) match {
          case Success(expires) => result = result.right.map(_.copy(expires = Some(expires)))
          case Failure(_)       => result = Left(s"Expires cookie attribute is not a valid RFC1123 datetime: $v")
        }
      case (k, Some(v)) if k.equalsIgnoreCase("max-age") =>
        Try(v.toLong) match {
          case Success(maxAge) => result = result.right.map(_.copy(maxAge = Some(maxAge)))
          case Failure(_)      => result = Left(s"Max-Age cookie attribute is not a number: $v")
        }
      case (k, v) if k.equalsIgnoreCase("domain")   => result = result.right.map(_.copy(domain = Some(v.getOrElse(""))))
      case (k, v) if k.equalsIgnoreCase("path")     => result = result.right.map(_.copy(path = Some(v.getOrElse(""))))
      case (k, _) if k.equalsIgnoreCase("secure")   => result = result.right.map(_.copy(secure = true))
      case (k, _) if k.equalsIgnoreCase("httponly") => result = result.right.map(_.copy(httpOnly = true))
      case (k, v)                                   => result = Left(s"Unknown cookie attribute: $k=${v.getOrElse("")}")
    }

    result
  }
}

case class CookiePair(name: String, value: String) {
  private val AllowedNameCharacters = """[a-zA-Z0-9!#$%&'*+\-.^_`|~]*""".r
  private val AllowedValueCharacters = """"?[a-zA-Z0-9!#$%&'()*+\-./:<=>?@\\[\\]^_`{|}~]*"?""".r

  require(
    AllowedNameCharacters.unapplySeq(name).isDefined,
    "Cookie name can only contain alphanumeric characters and: !#$%&'*+\\-.^_`|~"
  )

  require(
    AllowedValueCharacters.unapplySeq(name).isDefined,
    "Cookie value can only contain alphanumeric characters and: !#$%&'()*+\\-./:<=>?@[]^_`{|}~, optionally surrounded by \""
  )

  def asHeaderValue: String = s"$name=$value"
}

object CookiePair {
  def parseHeaderValue(s: String): List[CookiePair] = {
    s.split(";").toList.map { ss =>
      ss.split("=", 2).map(_.trim) match {
        case Array(v1)     => CookiePair(v1, "")
        case Array(v1, v2) => CookiePair(v1, v2)
      }
    }
  }
}
