package sttp.client

import sttp.client.model._
import org.scalatest.{FlatSpec, Matchers}
import sttp.client.model.HeaderNames

class RequestTests extends FlatSpec with Matchers {

  "content length" should "be automatically set for a string body" in {
    basicRequest
      .body("test")
      .headers
      .find(_._1.equalsIgnoreCase(HeaderNames.ContentLength))
      .map(_._2) should be(Some("4"))
  }

  it should "be automatically set to the number of utf-8 bytes in a string" in {
    basicRequest
      .body("ąęć")
      .headers
      .find(_._1.equalsIgnoreCase(HeaderNames.ContentLength))
      .map(_._2) should be(Some("6"))
  }

  it should "not override an already specified content length" in {
    basicRequest
      .contentLength(10)
      .body("a")
      .headers
      .find(_._1.equalsIgnoreCase(HeaderNames.ContentLength))
      .map(_._2) should be(Some("10"))
  }

  "request timeout" should "use default if not overridden" in {
    basicRequest.options.readTimeout should be(DefaultReadTimeout)
  }
}
