package sttp.model

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import sttp.model.internal.{Rfc2616, Validate}
import sttp.model.internal.Validate._
import sttp.model.internal.Rfc2616.validateToken

import scala.util.{Failure, Success, Try}

/**
  * A cookie name-value pair.
  *
  * The `name` and `value` should be already encoded (if necessary), as when serialised, they end up unmodified in
  * the header.
  */
case class Cookie private (name: String, value: String) {

  /**
    * @return Representation of the cookie as in a header value, in the format: `[name]=[value]`.
    */
  override def toString: String = s"$name=$value"
}

object Cookie {
  // see: https://stackoverflow.com/questions/1969232/allowed-characters-in-cookies/1969339
  private val AllowedValueCharacters = s"[^${Rfc2616.CTL}]*".r

  private[model] def validateName(name: String): Option[String] = validateToken("Cookie name", name)

  private[model] def validateValue(value: String): Option[String] = {
    if (AllowedValueCharacters.unapplySeq(value).isEmpty) {
      Some(
        "Cookie value can only contain alphanumeric characters, whitespace and: !#$%&'\"()*+\\-./:<=>?@[]^_`{|}~"
      )
    } else None
  }

  /**
    * @throws IllegalArgumentException If the cookie attributes contain illegal characters.
    */
  def unsafeApply(name: String, value: String): Cookie = safeApply(name, value).getOrThrow

  def safeApply(name: String, value: String): Either[String, Cookie] = {
    Validate.all(validateName(name), validateValue(value))(new Cookie(name, value))
  }

  def notValidated(name: String, value: String): Cookie = new Cookie(name, value)

  /**
    * Parse the cookie, represented as a header value (in the format: `[name]=[value]`).
    */
  def parse(s: String): Either[String, List[Cookie]] = {
    val cs = s.split(";").toList.map { ss =>
      ss.split("=", 2).map(_.trim) match {
        case Array(v1)     => Cookie.safeApply(v1, "")
        case Array(v1, v2) => Cookie.safeApply(v1, v2)
      }
    }

    Validate.sequence(cs)
  }

  /**
    * @return Representation of the cookies as in a header value, in the format: `[name]=[value]; [name]=[value]; ...`.
    */
  def toString(cs: List[Cookie]): String = cs.map(_.toString).mkString("; ")
}

case class CookieValueWithMeta private (
    value: String,
    expires: Option[Instant],
    maxAge: Option[Long],
    domain: Option[String],
    path: Option[String],
    secure: Boolean,
    httpOnly: Boolean
)

object CookieValueWithMeta {
  private val AllowedAttrValueCharacters = s"""[^;${Rfc2616.CTL}]*""".r

  private[model] def validateAttrValue(attrName: String, value: String): Option[String] = {
    if (AllowedAttrValueCharacters.unapplySeq(value).isEmpty) {
      Some(s"Value of attribute $attrName name can contain any characters except ; and control characters")
    } else None
  }
  def unsafeApply(
      value: String,
      expires: Option[Instant],
      maxAge: Option[Long],
      domain: Option[String],
      path: Option[String],
      secure: Boolean,
      httpOnly: Boolean
  ): CookieValueWithMeta =
    safeApply(value, expires, maxAge, domain, path, secure, httpOnly).getOrThrow

  def safeApply(
      value: String,
      expires: Option[Instant],
      maxAge: Option[Long],
      domain: Option[String],
      path: Option[String],
      secure: Boolean,
      httpOnly: Boolean
  ): Either[String, CookieValueWithMeta] = {
    Validate.all(
      Cookie.validateValue(value),
      path.flatMap(validateAttrValue("path", _)),
      domain.flatMap(validateAttrValue("domain", _))
    )(notValidated(value, expires, maxAge, domain, path, secure, httpOnly))
  }

  def notValidated(
      value: String,
      expires: Option[Instant],
      maxAge: Option[Long],
      domain: Option[String],
      path: Option[String],
      secure: Boolean,
      httpOnly: Boolean
  ): CookieValueWithMeta = new CookieValueWithMeta(value, expires, maxAge, domain, path, secure, httpOnly)
}

/**
  * A cookie name-value pair with attributes.
  *
  * All `String` values should be already encoded (if necessary), as when serialised, they end up unmodified in the
  * header.
  */
case class CookieWithMeta private (
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

  /**
    * @return Representation of the cookie as in a header value, in the format: `[name]=[value]; [attr]=[value]; ...`.
    */
  override def toString: String = {
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
  def unsafeApply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false
  ): CookieWithMeta =
    safeApply(name, value, expires, maxAge, domain, path, secure, httpOnly).getOrThrow

  def safeApply(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false
  ): Either[String, CookieWithMeta] = {
    Cookie.validateName(name) match {
      case Some(e) => Left(e)
      case None =>
        CookieValueWithMeta.safeApply(value, expires, maxAge, domain, path, secure, httpOnly).right.map { v =>
          notValidated(name, v)
        }
    }
  }

  def notValidated(
      name: String,
      value: String,
      expires: Option[Instant] = None,
      maxAge: Option[Long] = None,
      domain: Option[String] = None,
      path: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false
  ): CookieWithMeta =
    notValidated(name, CookieValueWithMeta.notValidated(value, expires, maxAge, domain, path, secure, httpOnly))

  def notValidated(
      name: String,
      valueWithMeta: CookieValueWithMeta
  ): CookieWithMeta = new CookieWithMeta(name, valueWithMeta)

  // https://tools.ietf.org/html/rfc6265#section-4.1.1
  /**
    * Parse the cookie, represented as a header value (in the format: `[name]=[value]; [attr]=[value]; ...`).
    */
  def parse(s: String): Either[String, CookieWithMeta] = {
    def splitkv(kv: String): (String, Option[String]) = kv.split("=", 2).map(_.trim) match {
      case Array(v1)     => (v1, None)
      case Array(v1, v2) => (v1, Some(v2))
    }

    val components = s.split(";").map(_.trim)
    val (first, other) = (components.head, components.tail)
    val (name, value) = splitkv(first)
    var result: Either[String, CookieWithMeta] = Right(CookieWithMeta.notValidated(name, value.getOrElse("")))
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
