package sttp.client

import sttp.model.{HeaderNames, StatusCode}
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
    val asLeft: ResponseAs[Left[String, String], Any] = asStringAlways.map(Left(_))
    val asRight: ResponseAs[Right[String, String], Any] = asStringAlways.map(Right(_))

    def myRequest: Request[Either[String, String], Any] =
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
      .show() shouldBe "GET https://test.com, response as: (either(as string, as params), as string), headers: Accept-Encoding: gzip, deflate, Authorization: ***, Content-Type: text/plain; charset=utf-8, Content-Length: 4, body: string: 1234"
  }

  it should "give meaningful information for a partial request" in {
    basicRequest
      .header(HeaderNames.Authorization, "secret")
      .body("1234")
      .response(asBoth(asParams, asStringAlways))
      .show() shouldBe "response as: (either(as string, as params), as string), headers: Accept-Encoding: gzip, deflate, Authorization: ***, Content-Type: text/plain; charset=utf-8, Content-Length: 4, body: string: 1234"
  }
}
