package sttp.model

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import scala.util.{Failure, Success, Try}

case class Cookie(name: String, value: String) {
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

object Cookie {
  def parseHeaderValue(s: String): List[Cookie] = {
    s.split(";").toList.map { ss =>
      ss.split("=", 2).map(_.trim) match {
        case Array(v1)     => Cookie(v1, "")
        case Array(v1, v2) => Cookie(v1, v2)
      }
    }
  }

  def asHeaderValue(cs: List[Cookie]): String = cs.map(_.asHeaderValue).mkString("; ")
}

case class CookieValueWithMeta(
    value: String,
    expires: Option[Instant],
    maxAge: Option[Long],
    domain: Option[String],
    path: Option[String],
    secure: Boolean,
    httpOnly: Boolean
)

case class CookieWithMeta(
    name: String,
    valueWithMeta: CookieValueWithMeta
) {
  def value: String = valueWithMeta.value
  def expires: Option[Instant] = valueWithMeta.expires
  def maxAge: Option[Long] = valueWithMeta.maxAge
  def domain: Option[String] = valueWithMeta.domain
  def path: Option[String] = valueWithMeta.path
  def secure: Boolean = valueWithMeta.secure
  def httpOnly: Boolean = valueWithMeta.httpOnly

  def value(v: String): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(value = v))
  def expires(v: Option[Instant]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(expires = v))
  def maxAge(v: Option[Long]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(maxAge = v))
  def domain(v: Option[String]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(domain = v))
  def path(v: Option[String]): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(path = v))
  def secure(v: Boolean): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(secure = v))
  def httpOnly(v: Boolean): CookieWithMeta = copy(valueWithMeta = valueWithMeta.copy(httpOnly = v))

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

object CookieWithMeta {
  def apply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false
  ): CookieWithMeta = CookieWithMeta(name, CookieValueWithMeta(value, expires, maxAge, domain, path, secure, httpOnly))

  // https://tools.ietf.org/html/rfc6265#section-4.1.1
  def parseHeaderValue(s: String): Either[String, CookieWithMeta] = {
    def splitkv(kv: String): (String, Option[String]) = kv.split("=", 2).map(_.trim) match {
      case Array(v1)     => (v1, None)
      case Array(v1, v2) => (v1, Some(v2))
    }

    val components = s.split(";").map(_.trim)
    val (first, other) = (components.head, components.tail)
    val (name, value) = splitkv(first)
    var result: Either[String, CookieWithMeta] = Right(CookieWithMeta(name, value.getOrElse("")))
    other.map(splitkv).map(t => (t._1.toLowerCase, t._2)).foreach {
      case (k, Some(v)) if k == "expires" =>
        Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(v))) match {
          case Success(expires) => result = result.right.map(_.expires(Some(expires)))
          case Failure(_)       => result = Left(s"Expires cookie attribute is not a valid RFC1123 datetime: $v")
        }
      case (k, Some(v)) if k == "max-age" =>
        Try(v.toLong) match {
          case Success(maxAge) => result = result.right.map(_.maxAge(Some(maxAge)))
          case Failure(_)      => result = Left(s"Max-Age cookie attribute is not a number: $v")
        }
      case (k, v) if k == "domain"   => result = result.right.map(_.domain(Some(v.getOrElse(""))))
      case (k, v) if k == "path"     => result = result.right.map(_.path(Some(v.getOrElse(""))))
      case (k, _) if k == "secure"   => result = result.right.map(_.secure(true))
      case (k, _) if k == "httponly" => result = result.right.map(_.httpOnly(true))
      case (k, v)                    => result = Left(s"Unknown cookie attribute: $k=${v.getOrElse("")}")
    }

    result
  }
}
