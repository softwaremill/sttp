package com.softwaremill.sttp

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, Matchers}

class ToCurlConverterTest extends FlatSpec with Matchers {

  private val localhost = uri"http://localhost"

  it should "convert base request" in {
    request
      .get(uri"$localhost")
      .toCurl shouldBe """curl -L --max-redirs 32 -X GET 'http://localhost'"""
  }

  it should "convert request with method to curl" in {
    request.get(localhost).toCurl should include("-X GET")
    request.post(localhost).toCurl should include("-X POST")
    request.put(localhost).toCurl should include("-X PUT")
    request.delete(localhost).toCurl should include("-X DELETE")
    request.patch(localhost).toCurl should include("-X PATCH")
    request.head(localhost).toCurl should include("-X HEAD")
    request.options(localhost).toCurl should include("-X OPTIONS")
  }

  it should "convert request with header" in {
    request.header("User-Agent", "myapp").get(localhost).toCurl should include(
      """-H 'User-Agent: myapp'"""
    )
  }

  it should "convert request with body" in {
    request.body(Map("name" -> "john", "org" -> "sml")).post(localhost).toCurl should include(
      """-H 'Content-Type: application/x-www-form-urlencoded' -H 'Content-Length: 17' -F 'name=john&org=sml'"""
    )
    request.body("name=john").post(localhost).toCurl should include(
      """-H 'Content-Type: text/plain; charset=utf-8' -H 'Content-Length: 9' --data 'name=john'"""
    )
    request.body("name=john", StandardCharsets.ISO_8859_1.name()).post(localhost).toCurl should include(
      """-H 'Content-Type: text/plain; charset=ISO-8859-1' -H 'Content-Length: 9' --data 'name=john'"""
    )
    request.body("name='john'").post(localhost).toCurl should include(
      """-H 'Content-Type: text/plain; charset=utf-8' -H 'Content-Length: 11' --data 'name=\'john\''"""
    )
    request.body("name=\"john\"").post(localhost).toCurl should include(
      """-H 'Content-Type: text/plain; charset=utf-8' -H 'Content-Length: 11' --data 'name="john"'"""
    )
  }

  it should "convert request with options" in {
    request.followRedirects(false).get(localhost).toCurl should not include "-L"
    request.maxRedirects(11).get(localhost).toCurl should include("--max-redirs 11")
  }

  it should "put placeholder when sending binary data" in {
    val testBodyBytes = "this is the body".getBytes("UTF-8")

    val curl = request
      .post(localhost)
      .body(new ByteArrayInputStream(testBodyBytes))
      .toCurl
    curl should include("--data-binary <PLACEHOLDER>")
  }
}
