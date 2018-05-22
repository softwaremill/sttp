package com.softwaremill.sttp

import org.scalatest.{FlatSpec, Matchers}

class RequestTests extends FlatSpec with Matchers {

  "content length" should "be automatically set for a string body" in {
    sttp
      .body("test")
      .headers
      .find(_._1.equalsIgnoreCase(ContentLengthHeader))
      .map(_._2) should be(Some("4"))
  }

  it should "be automatically set to the number of utf-8 bytes in a string" in {
    sttp
      .body("ąęć")
      .headers
      .find(_._1.equalsIgnoreCase(ContentLengthHeader))
      .map(_._2) should be(Some("6"))
  }

  it should "not override an already specified content length" in {
    sttp
      .contentLength(10)
      .body("a")
      .headers
      .find(_._1.equalsIgnoreCase(ContentLengthHeader))
      .map(_._2) should be(Some("10"))
  }

  "request timeout" should "use default if not overridden" in {
    sttp.options.readTimeout should be(DefaultReadTimeout)
  }
}
