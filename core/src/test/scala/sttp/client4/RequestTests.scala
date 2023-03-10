package sttp.client4

import sttp.model.{Header, HeaderNames, StatusCode}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RequestTests extends AnyFlatSpec with Matchers {

  "content length" should "be automatically set for a string body" in {
    basicRequest
      .body("test")
      .headers
      .find(_.name.equalsIgnoreCase(HeaderNames.ContentLength))
      .map(_.value) should be(Some("4"))
  }

  it should "be automatically set to the number of utf-8 bytes in a string" in {
    basicRequest
      .body("ąęć")
      .headers
      .find(_.name.equalsIgnoreCase(HeaderNames.ContentLength))
      .map(_.value) should be(Some("6"))
  }

  it should "not override an already specified content length" in {
    basicRequest
      .contentLength(10)
      .body("a")
      .headers
      .find(_.name.equalsIgnoreCase(HeaderNames.ContentLength))
      .map(_.value) should be(Some("10"))
  }

  "request timeout" should "use default if not overridden" in {
    basicRequest.options.readTimeout should be(DefaultReadTimeout)
  }

  "multiple subtype response variants" should "compile" in {
    val asLeft: ResponseAs[Left[String, String]] = asStringAlways.map(Left(_))
    val asRight: ResponseAs[Right[String, String]] = asStringAlways.map(Right(_))

    basicRequest
      .get(uri"https://test.com")
      .response {
        fromMetadata(asRight, ConditionalResponseAs(_.code == StatusCode.Ok, asLeft))
      }
  }

  "show" should "give meaningful information" in {
    basicRequest
      .get(uri"https://test.com")
      .header(HeaderNames.Authorization, "secret")
      .body("1234")
      .response(asBoth(asParams, asStringAlways))
      .show() shouldBe
      "GET https://test.com, response as: (either(as string, as params), as string), headers: Accept-Encoding: gzip, deflate, Authorization: ***, Content-Type: text/plain; charset=utf-8, Content-Length: 4, body: string: 1234"
  }

  it should "give meaningful information for a partial request" in {
    basicRequest
      .header(HeaderNames.Authorization, "secret")
      .body("1234")
      .response(asBoth(asParams, asStringAlways))
      .show() shouldBe
      "(no method & uri set), response as: (either(as string, as params), as string), headers: Accept-Encoding: gzip, deflate, Authorization: ***, Content-Type: text/plain; charset=utf-8, Content-Length: 4, body: string: 1234"
  }

  it should "properly replace headers" in {
    emptyRequest.header("H1", "V1").header("H1", "V2").headers shouldBe List(Header("H1", "V1"), Header("H1", "V2"))
    emptyRequest.header("H1", "V1").header("H1", "V2", replaceExisting = true).headers shouldBe List(Header("H1", "V2"))

    emptyRequest.header(Header("H1", "V1")).header(Header("H1", "V2")).headers shouldBe List(
      Header("H1", "V1"),
      Header("H1", "V2")
    )
    emptyRequest.header(Header("H1", "V1")).header(Header("H1", "V2"), replaceExisting = true).headers shouldBe List(
      Header("H1", "V2")
    )

    emptyRequest
      .headers(Map("H1" -> "V1", "H2" -> "V2"))
      .headers(Map("H1" -> "V11", "H3" -> "V3"))
      .headers
      .toSet shouldBe Set(Header("H1", "V1"), Header("H2", "V2"), Header("H1", "V11"), Header("H3", "V3"))
    emptyRequest
      .headers(Map("H1" -> "V1", "H2" -> "V2"))
      .headers(Map("H1" -> "V11", "H3" -> "V3"), replaceExisting = true)
      .headers
      .toSet shouldBe Set(Header("H2", "V2"), Header("H1", "V11"), Header("H3", "V3"))

    emptyRequest
      .headers(Header("H1", "V1"), Header("H2", "V2"))
      .headers(Header("H1", "V11"), Header("H3", "V3"))
      .headers shouldBe List(Header("H1", "V1"), Header("H2", "V2"), Header("H1", "V11"), Header("H3", "V3"))
    emptyRequest
      .headers(Header("H1", "V1"), Header("H2", "V2"))
      .headers(List(Header("H1", "V11"), Header("H3", "V3")), replaceExisting = true)
      .headers shouldBe List(Header("H2", "V2"), Header("H1", "V11"), Header("H3", "V3"))
  }
}
