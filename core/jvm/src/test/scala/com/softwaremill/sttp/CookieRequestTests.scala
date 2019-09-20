package com.softwaremill.sttp

import org.scalatest.{FlatSpec, Matchers}
import com.softwaremill.sttp.model._

class CookieRequestTests extends FlatSpec with Matchers {

  "request cookies" should "be set from a name-value pair" in {
    request
      .cookie("k", "v")
      .headers
      .find(_._1 == HeaderNames.Cookie)
      .map(_._2) should be(Some("k=v"))
  }

  it should "be set from multiple name-value pairs" in {
    request
      .cookies("k1" -> "v1", "k2" -> "v2")
      .headers
      .find(_._1 == HeaderNames.Cookie)
      .map(_._2) should be(Some("k1=v1; k2=v2"))
  }

  it should "add multiple headers if invoked multiple times" in {
    request
      .cookie("k1", "v1")
      .cookie("k2" -> "v2")
      .headers
      .filter(_._1 == HeaderNames.Cookie)
      .map(_._2)
      .toSet should be(Set("k1=v1", "k2=v2"))
  }

  it should "set cookies from a response" in {
    val response =
      Response(Right(()), 0, "", List((HeaderNames.SetCookie, "k1=v1"), (HeaderNames.SetCookie, "k2=v2")), Nil)
    request
      .cookies(response)
      .headers
      .find(_._1 == HeaderNames.Cookie)
      .map(_._2) should be(Some("k1=v1; k2=v2"))
  }

}
