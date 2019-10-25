package sttp.model

import sttp.model.internal.{Rfc2616, Validate}
import sttp.model.internal.Validate._

import scala.util.hashing.MurmurHash3

/**
  * An HTTP header. The `name` property is case-insensitive during equality checks.
  *
  * The `name` and `value` should be already encoded (if necessary), as when serialised, they end up unmodified in
  * the header.
  */
class Header(val name: String, val value: String) {

  /**
    * Check if the name of this header is the same as the given one. The names are compared in a case-insensitive way.
    */
  def is(otherName: String): Boolean = name.equalsIgnoreCase(otherName)

  /**
    * @return Representation in the format: `[name]: [value]`.
    */
  override def toString: String = s"$name: $value"
  override def hashCode(): Int = MurmurHash3.mixLast(name.toLowerCase.hashCode, value.hashCode)
  override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this.eq(h) => true
    case h: Header               => is(h.name) && (value == h.value)
    case _                       => false
  }
}

object Header {
  def unapply(h: Header): Option[(String, String)] = Some((h.name, h.value))

  private[model] def validateName(name: String): Option[String] = {
    if (Rfc2616.Token.unapplySeq(name).isEmpty) {
      Some("Header name can not contain separators: ()<>@,;:\\\"/[]?={}, or whitespace.")
    } else None
  }

  /**
    * @throws IllegalArgumentException If the header name contains illegal characters.
    */
  def unsafeApply(name: String, value: String): Header = validated(name, value).getOrThrow

  def validated(name: String, value: String): Either[String, Header] = {
    Validate.all(validateName(name))(new Header(name, value))
  }

  def notValidated(name: String, value: String): Header = new Header(name, value)

  //

  def accept(mediaRanges: String): Header = Header.notValidated(HeaderNames.Accept, mediaRanges)
  def acceptCharset(charsetRanges: String): Header = Header.notValidated(HeaderNames.AcceptCharset, charsetRanges)
  def acceptEncoding(encodingRanges: String): Header = Header.notValidated(HeaderNames.AcceptEncoding, encodingRanges)
  def accessControlAllowCredentials(allow: Boolean): Header =
    Header.notValidated(HeaderNames.AccessControlAllowCredentials, allow.toString)
  def accessControlAllowHeaders(headerNames: String*): Header =
    Header.notValidated(HeaderNames.AccessControlAllowHeaders, headerNames.mkString(", "))
  def accessControlAllowMethods(methods: Method*): Header =
    Header.notValidated(HeaderNames.AccessControlAllowMethods, methods.map(_.method).mkString(", "))
  def accessControlAllowOrigin(originRange: String): Header =
    Header.notValidated(HeaderNames.AccessControlAllowOrigin, originRange)
  def accessControlExposeHeaders(headerNames: String*): Header =
    Header.notValidated(HeaderNames.AccessControlExposeHeaders, headerNames.mkString(", "))
  def accessControlMaxAge(deltaSeconds: Long): Header =
    Header.notValidated(HeaderNames.AccessControlMaxAge, deltaSeconds.toString)
  def accessControlRequestHeaders(headerNames: String*): Header =
    Header.notValidated(HeaderNames.AccessControlRequestHeaders, headerNames.mkString(", "))
  def accessControlRequestMethod(method: Method): Header =
    Header.notValidated(HeaderNames.AccessControlRequestMethod, method.toString)
  def authorization(authType: String, credentials: String): Header =
    Header.notValidated(HeaderNames.Authorization, s"$authType $credentials")
  def contentLength(length: Long): Header = Header.notValidated(HeaderNames.ContentLength, length.toString)
  def contentEncoding(encoding: String): Header = Header.notValidated(HeaderNames.ContentEncoding, encoding)
  def contentType(mediaType: MediaType): Header = Header.notValidated(HeaderNames.ContentType, mediaType.toString)
  def cookie(firstCookie: Cookie, otherCookies: Cookie*): Header =
    Header.notValidated(HeaderNames.Cookie, (firstCookie +: otherCookies).map(_.toString).mkString("; "))
  def etag(tag: String): Header = Header.notValidated(HeaderNames.Etag, tag)
  def proxyAuthorization(authType: String, credentials: String): Header =
    Header.notValidated(HeaderNames.ProxyAuthorization, s"$authType $credentials")
  def setCookie(cookie: CookieWithMeta): Header = Header.notValidated(HeaderNames.SetCookie, cookie.toString)
  def userAgent(userAgent: String): Header = Header.notValidated(HeaderNames.UserAgent, userAgent)
  def xForwardedFor(firstAddress: String, otherAddresses: String*): Header =
    Header.notValidated(HeaderNames.XForwardedFor, (firstAddress +: otherAddresses).mkString(", "))
}
