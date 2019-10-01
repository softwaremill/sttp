package sttp.model

import java.time.{ZoneId, ZonedDateTime}

import org.scalatest.{FlatSpec, Matchers}

class CookieTest extends FlatSpec with Matchers {
  val parseCookieData = List(
    "user_id=5; Expires=Fri, 5 Oct 2018 14:28:00 GMT; Secure; HttpOnly" -> Right(
      CookieWithMeta(
        "user_id",
        "5",
        Some(ZonedDateTime.of(2018, 10, 5, 14, 28, 0, 0, ZoneId.of("GMT")).toInstant),
        secure = true,
        httpOnly = true
      )
    ),
    """_myapp_session={"user_id": "5"}""" -> Right(CookieWithMeta("_myapp_session", """{"user_id": "5"}""")),
    "x=y; Max-Age=123; Domain=example.com; Path=/x/z/y" -> Right(
      CookieWithMeta("x", "y", maxAge = Some(123), domain = Some("example.com"), path = Some("/x/z/y"))
    ),
    "" -> Right(CookieWithMeta("", "")),
    "x=y; Max-Age=z" -> Left("Max-Age cookie attribute is not a number: z")
  )

  for ((cookieHeaderValue, expectedResult) <- parseCookieData) {
    it should s"parse or error: $cookieHeaderValue" in {
      CookieWithMeta.parseHeaderValue(cookieHeaderValue) shouldBe expectedResult
    }
  }

  val serializeCookieData = List(
    CookieWithMeta("x", "y", maxAge = Some(123), domain = Some("example.com"), path = Some("/x/z/y")) -> "x=y; Max-Age=123; Domain=example.com; Path=/x/z/y",
    CookieWithMeta("x", """"a"""") -> """x="a"""",
    CookieWithMeta(
      "user_id",
      "5",
      Some(ZonedDateTime.of(2018, 10, 5, 14, 28, 0, 0, ZoneId.of("GMT")).toInstant),
      secure = true,
      httpOnly = true
    ) -> "user_id=5; Expires=Fri, 5 Oct 2018 14:28:00 GMT; Secure; HttpOnly"
  )

  for ((cookie, expectedResult) <- serializeCookieData) {
    it should s"serialize: $cookie" in {
      cookie.asHeaderValue shouldBe expectedResult
    }
  }

  it should "parse single cookie pair" in {
    Cookie.parseHeaderValue("x=y") shouldBe List(Cookie("x", "y"))
  }

  it should "parse multiple cookie pairs" in {
    Cookie.parseHeaderValue("x=y; a=b; c; z=10") shouldBe List(
      Cookie("x", "y"),
      Cookie("a", "b"),
      Cookie("c", ""),
      Cookie("z", "10")
    )
  }

  it should "serialize cookie pair" in {
    Cookie("x", "y").asHeaderValue shouldBe "x=y"
  }
}
