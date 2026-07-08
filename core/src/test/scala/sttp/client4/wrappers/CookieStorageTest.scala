package sttp.client4.wrappers

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client4._

class CookieStorageTest extends AnyFunSuite with Matchers {
  private def names(cs: Seq[(String, String)]) = cs.map(_._1).toSet

  test("stores a host-only cookie and sends it back to the same host") {
    val storage = CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/a", List("s=1"))
    names(storage.cookiesFor(uri"https://example.com/b")) shouldBe Set("s")
  }

  test("does not send a host-only cookie to a subdomain") {
    val storage = CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/", List("s=1"))
    storage.cookiesFor(uri"https://sub.example.com/") shouldBe empty
  }

  test("sends a domain cookie to matching subdomains, but not to unrelated domains") {
    val storage =
      CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/", List("s=1; Domain=example.com"))
    names(storage.cookiesFor(uri"https://sub.example.com/")) shouldBe Set("s")
    storage.cookiesFor(uri"https://other.com/") shouldBe empty
  }

  test("rejects a cookie whose domain does not match the setting host") {
    val storage = CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/", List("s=1; Domain=evil.com"))
    storage.isEmpty shouldBe true
  }

  test("normalizes the cookie domain (leading dot, case)") {
    val storage =
      CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/", List("s=1; Domain=.EXAMPLE.com"))
    names(storage.cookiesFor(uri"https://sub.example.com/")) shouldBe Set("s")
  }

  test("respects the cookie path, requiring a path-segment boundary") {
    val storage = CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/admin", List("s=1; Path=/admin"))
    names(storage.cookiesFor(uri"https://example.com/admin/x")) shouldBe Set("s")
    storage.cookiesFor(uri"https://example.com/administrator") shouldBe empty // prefix, but not a segment boundary
    storage.cookiesFor(uri"https://example.com/public") shouldBe empty
  }

  test("defaults a path-less cookie to the setting request's directory (RFC 6265 5.1.4)") {
    val storage = CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/admin/page", List("s=1"))
    names(storage.cookiesFor(uri"https://example.com/admin/other")) shouldBe Set("s")
    storage.cookiesFor(uri"https://example.com/elsewhere") shouldBe empty
  }

  test("does not send a secure cookie over http") {
    val storage = CookieStorage.empty.setFromSetCookieHeaders(uri"https://example.com/", List("s=1; Secure"))
    storage.cookiesFor(uri"http://example.com/") shouldBe empty
    names(storage.cookiesFor(uri"https://example.com/")) shouldBe Set("s")
  }

  test("a later cookie with the same name/domain/path overwrites the earlier one") {
    val storage = CookieStorage.empty
      .setFromSetCookieHeaders(uri"https://example.com/", List("s=1"))
      .setFromSetCookieHeaders(uri"https://example.com/", List("s=2"))
    storage.cookiesFor(uri"https://example.com/") shouldBe Seq("s" -> "2")
  }

  test("a cookie with Max-Age <= 0 removes a matching stored cookie") {
    val storage = CookieStorage.empty
      .setFromSetCookieHeaders(uri"https://example.com/", List("s=1"))
      .setFromSetCookieHeaders(uri"https://example.com/", List("s=; Max-Age=0"))
    storage.isEmpty shouldBe true
  }
}
