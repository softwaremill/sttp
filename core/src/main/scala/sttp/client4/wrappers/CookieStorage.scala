package sttp.client4.wrappers

import sttp.attributes.AttributeKey
import sttp.model.Uri

/** An immutable cookie jar.
  *
  * Collects cookies received in `Set-Cookie` response headers and determines which of them should be sent with a
  * request to a given URI, applying a subset of the [[https://www.rfc-editor.org/rfc/rfc6265 RFC 6265]] rules:
  * domain-matching, path-matching and the `Secure` attribute.
  *
  * Intended use: attach a storage to a request using [[sttp.client4.RequestBuilder.cookieStorage]]. The
  * [[FollowRedirectsBackend]] (applied to all backends by default) then, for each request in a redirect chain, sends
  * the matching stored cookies and threads an updated storage through to the next request. This makes cookies set via
  * `Set-Cookie` during a redirect chain visible to subsequent requests in that chain - which otherwise doesn't happen,
  * as the `Cookie` header is a sensitive header, stripped when following redirects.
  *
  * Cookies are represented as plain name/value pairs (rather than [[sttp.model.headers.CookieWithMeta]]) so that the
  * storage is usable on all platforms, including Scala Native. Time-based expiry (`Max-Age` > 0, `Expires`) is not
  * tracked, as the storage has no notion of the current time; a `Set-Cookie` with `Max-Age` <= 0 removes a matching
  * cookie, so a server can still clear cookies within a chain.
  */
final class CookieStorage private (private val entries: Map[CookieStorage.Key, CookieStorage.Stored]) {
  import CookieStorage._

  /** A new storage updated with the cookies parsed from the `Set-Cookie` header values received from `setBy`.
    * Following RFC 6265, a cookie whose `Domain` attribute does not domain-match `setBy` is rejected (to prevent a
    * host setting cookies for unrelated domains). A cookie with `Max-Age` <= 0 removes a matching stored cookie.
    */
  def setFromSetCookieHeaders(setBy: Uri, setCookieHeaders: Iterable[String]): CookieStorage = {
    val host = hostOf(setBy)
    val updated = setCookieHeaders.flatMap(parseSetCookie).foldLeft(entries) { (acc, c) =>
      val hostOnly = c.domain.isEmpty
      val domain = c.domain.map(normalizeDomain).getOrElse(host)
      if (domain.isEmpty || !domainMatches(host, domain)) acc
      else {
        val key = Key(c.name, domain, c.path.getOrElse(DefaultPath))
        if (c.removed) acc - key
        else acc.updated(key, Stored(c.value, c.secure, hostOnly))
      }
    }
    new CookieStorage(updated)
  }

  /** The cookies, as `name -> value` pairs, that should be sent with a request to `uri`, according to domain-matching,
    * path-matching and the `Secure` attribute (secure cookies are only sent over `https`).
    */
  def cookiesFor(uri: Uri): Seq[(String, String)] = {
    val host = hostOf(uri)
    val path = pathOf(uri)
    val secure = uri.scheme.exists(_.equalsIgnoreCase("https"))
    entries.collect {
      case (key, stored)
          if (if (stored.hostOnly) host == key.domain else domainMatches(host, key.domain)) &&
            pathMatches(path, key.path) &&
            (!stored.secure || secure) =>
        key.name -> stored.value
    }.toSeq
  }

  def isEmpty: Boolean = entries.isEmpty
}

object CookieStorage {

  /** An empty storage. */
  val empty: CookieStorage = new CookieStorage(Map.empty)

  /** The attribute key under which a [[CookieStorage]] is attached to a request; see
    * [[sttp.client4.RequestBuilder.cookieStorage]].
    */
  val attributeKey: AttributeKey[CookieStorage] =
    new AttributeKey[CookieStorage]("sttp.client4.wrappers.CookieStorage")

  private val DefaultPath = "/"

  // a cookie is identified by its name, the domain it's scoped to and its path
  private case class Key(name: String, domain: String, path: String)
  private case class Stored(value: String, secure: Boolean, hostOnly: Boolean)

  // a `Set-Cookie` cookie before its domain is resolved against the setting host; `removed` marks a Max-Age <= 0
  // deletion
  private case class Parsed(
      name: String,
      value: String,
      domain: Option[String],
      path: Option[String],
      secure: Boolean,
      removed: Boolean
  )

  private def hostOf(uri: Uri): String = uri.host.getOrElse("").toLowerCase
  private def pathOf(uri: Uri): String = "/" + uri.path.mkString("/")
  private def normalizeDomain(d: String): String = d.stripPrefix(".").toLowerCase

  // RFC 6265, 5.1.3: equal, or `host` is a subdomain of `domain`
  private def domainMatches(host: String, domain: String): Boolean =
    host == domain || host.endsWith("." + domain)

  // RFC 6265, 5.1.4
  private def pathMatches(requestPath: String, cookiePath: String): Boolean =
    requestPath == cookiePath ||
      (requestPath.startsWith(cookiePath) &&
        (cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length) == '/'))

  // A minimal `Set-Cookie` parser reading only the attributes used for storage. CookieWithMeta isn't used, as it isn't
  // available on Scala Native (its date handling depends on java.time formatting); `Expires` is ignored for the same
  // reason - storage doesn't track time-based expiry anyway.
  private def parseSetCookie(headerValue: String): Option[Parsed] =
    headerValue.split(";").iterator.map(_.trim).filter(_.nonEmpty).toList match {
      case nameValue :: directives =>
        val eq = nameValue.indexOf('=')
        val name = if (eq < 0) nameValue else nameValue.substring(0, eq).trim
        if (name.isEmpty) None
        else {
          val value = if (eq < 0) "" else nameValue.substring(eq + 1).trim
          val attrs = directives.map { d =>
            val i = d.indexOf('=')
            if (i < 0) (d.toLowerCase, "") else (d.substring(0, i).trim.toLowerCase, d.substring(i + 1).trim)
          }.toMap
          val maxAge = attrs.get("max-age").flatMap(s => scala.util.Try(s.toLong).toOption)
          Some(
            Parsed(
              name = name,
              value = value,
              domain = attrs.get("domain").filter(_.nonEmpty),
              path = attrs.get("path").filter(_.nonEmpty),
              secure = attrs.contains("secure"),
              removed = maxAge.exists(_ <= 0)
            )
          )
        }
      case Nil => None
    }
}
