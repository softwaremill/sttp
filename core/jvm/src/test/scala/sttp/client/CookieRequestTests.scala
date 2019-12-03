package sttp.client

import sttp.model.{StatusCode, _}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CookieRequestTests extends AnyFlatSpec with Matchers {

  "request cookies" should "be set from a name-value pair" in {
    basicRequest
      .cookie("k", "v")
      .headers
      .find(_.name == HeaderNames.Cookie)
      .map(_.value) should be(Some("k=v"))
  }

  it should "be set from multiple name-value pairs" in {
    basicRequest
      .cookies("k1" -> "v1", "k2" -> "v2")
      .headers
      .find(_.name == HeaderNames.Cookie)
      .map(_.value) should be(Some("k1=v1; k2=v2"))
  }

  it should "add multiple headers if invoked multiple times" in {
    basicRequest
      .cookie("k1", "v1")
      .cookie("k2" -> "v2")
      .cookies("k3" -> "v3", "k4" -> "v4")
      .headers
      .filter(_.name == HeaderNames.Cookie)
      .map(_.value)
      .toList should be(List("k1=v1; k2=v2; k3=v3; k4=v4"))
  }

  it should "set cookies from a response" in {
    val response =
      Response(
        Right(()),
        StatusCode.Ok,
        "",
        List(Header.notValidated(HeaderNames.SetCookie, "k1=v1"), Header.notValidated(HeaderNames.SetCookie, "k2=v2")),
        Nil
      )
    basicRequest
      .cookies(response)
      .headers
      .find(_.name == HeaderNames.Cookie)
      .map(_.value) should be(Some("k1=v1; k2=v2"))
  }

}
