package com.softwaremill.sttp

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class CookieRequestTests extends FlatSpec with Matchers {

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
      Response(Right(()), 0, "", List((SetCookieHeader, "k1=v1"), (SetCookieHeader, "k2=v2")), Nil)
    sttp
      .cookies(response)
      .headers
      .find(_._1 == CookieHeader)
      .map(_._2) should be(Some("k1=v1; k2=v2"))
  }

}
