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

  it should "compile multiple subtype response variants" in {
    val asLeft: ResponseAs[Left[String, String], Nothing] = asStringAlways.map(Left(_))
    val asRight: ResponseAs[Right[String, String], Nothing] = asStringAlways.map(Right(_))

    def myRequest: Request[Either[String, String], Nothing] =
      basicRequest
        .get(uri"https://test.com")
        .response {
          fromMetadata { meta =>
            meta.code match {
              case StatusCode.Ok         => asLeft
              case StatusCode.BadRequest => asRight
            }
          }
        }
  }
}
