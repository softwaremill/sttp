package com.softwaremill.sttp

import org.scalatest.{FlatSpec, Matchers}

class RequestTests extends FlatSpec with Matchers {
  "request cookies" should "be set from a name-value pair" in {
    sttp
      .cookie("k", "v")
      .headers
      .find(_._1 == CookieHeader)
      .map(_._2) should be(Some("k=v"))
  }

  it should "be set from multiple name-value pairs" in {
    sttp
      .cookies("k1" -> "v1", "k2" -> "v2")
      .headers
      .find(_._1 == CookieHeader)
      .map(_._2) should be(Some("k1=v1; k2=v2"))
  }

  it should "add multiple headers if invoked multiple times" in {
    sttp
      .cookie("k1", "v1")
      .cookie("k2" -> "v2")
      .headers
      .filter(_._1 == CookieHeader)
      .map(_._2)
      .toSet should be(Set("k1=v1", "k2=v2"))
  }

  it should "set cookies from a response" in {
    val response =
      Response(Right(()),
               0,
               "",
               List((SetCookieHeader, "k1=v1"), (SetCookieHeader, "k2=v2")),
               Nil)
    sttp
      .cookies(response)
      .headers
      .find(_._1 == CookieHeader)
      .map(_._2) should be(Some("k1=v1; k2=v2"))
  }

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
