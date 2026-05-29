package sttp.client4.wrappers

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.headers.CookieWithMeta
import sttp.client4._

class CookieStorageTest extends AnyFunSuite with Matchers {
  private def names(cs: Seq[CookieWithMeta]) = cs.map(_.name).toSet

  test("stores a host-only cookie and sends it back to the same host") {
    val storage = CookieStorage.empty.set(uri"https://example.com/a", List(CookieWithMeta("s", "1")))
    names(storage.forUri(uri"https://example.com/b")) shouldBe Set("s")
  }

  test("does not send a host-only cookie to a subdomain") {
    val storage = CookieStorage.empty.set(uri"https://example.com/", List(CookieWithMeta("s", "1")))
    storage.forUri(uri"https://sub.example.com/") shouldBe empty
  }

  test("sends a domain cookie to matching subdomains, but not to unrelated domains") {
    val cookie = CookieWithMeta("s", "1", domain = Some("example.com"))
    val storage = CookieStorage.empty.set(uri"https://example.com/", List(cookie))
    names(storage.forUri(uri"https://sub.example.com/")) shouldBe Set("s")
    storage.forUri(uri"https://other.com/") shouldBe empty
  }

  test("rejects a cookie whose domain does not match the setting host") {
    val cookie = CookieWithMeta("s", "1", domain = Some("evil.com"))
    val storage = CookieStorage.empty.set(uri"https://example.com/", List(cookie))
    storage.isEmpty shouldBe true
  }

  test("respects the cookie path") {
    val cookie = CookieWithMeta("s", "1", path = Some("/admin"))
    val storage = CookieStorage.empty.set(uri"https://example.com/admin", List(cookie))
    names(storage.forUri(uri"https://example.com/admin/x")) shouldBe Set("s")
    storage.forUri(uri"https://example.com/public") shouldBe empty
  }

  test("does not send a secure cookie over http") {
    val cookie = CookieWithMeta("s", "1", secure = true)
    val storage = CookieStorage.empty.set(uri"https://example.com/", List(cookie))
    storage.forUri(uri"http://example.com/") shouldBe empty
    names(storage.forUri(uri"https://example.com/")) shouldBe Set("s")
  }

  test("a later cookie with the same name/domain/path overwrites the earlier one") {
    val storage = CookieStorage.empty
      .set(uri"https://example.com/", List(CookieWithMeta("s", "1")))
      .set(uri"https://example.com/", List(CookieWithMeta("s", "2")))
    storage.forUri(uri"https://example.com/").map(_.value) shouldBe Seq("2")
  }

  test("a cookie with Max-Age <= 0 removes a matching stored cookie") {
    val storage = CookieStorage.empty
      .set(uri"https://example.com/", List(CookieWithMeta("s", "1")))
      .set(uri"https://example.com/", List(CookieWithMeta("s", "", maxAge = Some(0))))
    storage.isEmpty shouldBe true
  }
}
