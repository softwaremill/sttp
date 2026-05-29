package sttp.client4.wrappers

import sttp.attributes.AttributeKey
import sttp.model.Uri
import sttp.model.headers.CookieWithMeta

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
  * Time-based expiry (`Max-Age` > 0, `Expires`) is not tracked, as the storage has no notion of the current time; a
  * `Set-Cookie` with `Max-Age` <= 0 removes a matching cookie, so a server can still clear cookies within a chain.
  */
final class CookieStorage private (private val entries: Map[CookieStorage.Key, CookieStorage.Stored]) {
  import CookieStorage._

  /** A new storage updated with the `cookies` received in a response from `setBy`. Following RFC 6265, a cookie whose
    * `Domain` attribute does not domain-match `setBy` is rejected (to prevent a host setting cookies for unrelated
    * domains). A cookie with `Max-Age` <= 0 removes a matching stored cookie.
    */
  def set(setBy: Uri, cookies: Iterable[CookieWithMeta]): CookieStorage = {
    val host = hostOf(setBy)
    val updated = cookies.foldLeft(entries) { (acc, c) =>
      val hostOnly = c.domain.isEmpty
      val domain = c.domain.map(normalizeDomain).getOrElse(host)
      if (domain.isEmpty || !domainMatches(host, domain)) acc
      else {
        val key = Key(c.name, domain, c.path.getOrElse(DefaultPath))
        if (c.maxAge.exists(_ <= 0)) acc - key
        else acc.updated(key, Stored(c, hostOnly))
      }
    }
    new CookieStorage(updated)
  }

  /** The cookies that should be sent with a request to `uri`, according to domain-matching, path-matching and the
    * `Secure` attribute (secure cookies are only sent over `https`).
    */
  def forUri(uri: Uri): Seq[CookieWithMeta] = {
    val host = hostOf(uri)
    val path = pathOf(uri)
    val secure = uri.scheme.exists(_.equalsIgnoreCase("https"))
    entries.collect {
      case (key, Stored(cookie, hostOnly))
          if (if (hostOnly) host == key.domain else domainMatches(host, key.domain)) &&
            pathMatches(path, key.path) &&
            (!cookie.secure || secure) =>
        cookie
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
  private case class Stored(cookie: CookieWithMeta, hostOnly: Boolean)

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
}
