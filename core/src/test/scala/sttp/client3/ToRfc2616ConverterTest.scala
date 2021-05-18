package sttp.client3

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets

class ToRfc2616ConverterTest extends AnyFlatSpec with Matchers {

  private val localhost = uri"http://localhost"

  it should "convert base request" in {
    basicRequest
      .get(uri"$localhost")
      .toRfc2616Format shouldBe "GET http://localhost"
  }

  it should "convert request with method to curl" in {
    basicRequest.get(localhost).toRfc2616Format should startWith("GET")
    basicRequest.post(localhost).toRfc2616Format should startWith("POST")
    basicRequest.put(localhost).toRfc2616Format should startWith("PUT")
    basicRequest.delete(localhost).toRfc2616Format should startWith("DELETE")
    basicRequest.patch(localhost).toRfc2616Format should startWith("PATCH")
    basicRequest.head(localhost).toRfc2616Format should startWith("HEAD")
    basicRequest.options(localhost).toRfc2616Format should startWith("OPTIONS")
  }

  it should "convert request with header" in {
    basicRequest
      .header("User-Agent", "myapp")
      .header("Content-Type", "application/json")
      .get(localhost).toRfc2616Format should include(
      """User-Agent: myapp
        |Content-Type: application/json""".stripMargin
    )
  }

  it should "convert request with body" in {
    basicRequest.body(Map("name" -> "john", "org" -> "sml")).post(localhost).toRfc2616Format should include(
      """Content-Type: application/x-www-form-urlencoded
        |Content-Length: 17
        |
        |name=john&org=sml""".stripMargin
    )
    basicRequest.body("name=john").post(localhost).toRfc2616Format should include(
      """Content-Type: text/plain; charset=utf-8
        |Content-Length: 9
        |
        |name=john""".stripMargin
    )
    basicRequest.body("name=john", StandardCharsets.ISO_8859_1.name()).post(localhost).toRfc2616Format should include(
      """Content-Type: text/plain; charset=ISO-8859-1
        |Content-Length: 9
        |
        |name=john""".stripMargin
    )
    basicRequest.body("name=\"john\"").post(localhost).toRfc2616Format should include(
      """Content-Type: text/plain; charset=utf-8
        |Content-Length: 11
        |
        |name="john"""".stripMargin
    )
    val xmlBody = """<request>
                    |    <name>sample</name>
                    |    <time>Wed, 21 Oct 2015 18:27:50 GMT</time>
                    |</request>""".stripMargin
    basicRequest
      .header("Authorization", "token")
      .contentType("application/xml")
      .body(xmlBody)
      .post(localhost).toRfc2616Format should include(
      """Authorization: token
        |Content-Type: application/xml
        |Content-Length: 91
        |
        |<request>
        |    <name>sample</name>
        |    <time>Wed, 21 Oct 2015 18:27:50 GMT</time>
        |</request>""".stripMargin
    )
    val jsonBody = """{
                     |    "name": "sample",
                     |    "time": "Wed, 21 Oct 2015 18:27:50 GMT"
                     |}""".stripMargin
    basicRequest
      .header("Authorization", "token")
      .contentType("application/json")
      .body(jsonBody)
      .post(localhost).toRfc2616Format should include(
      """Authorization: token
        |Content-Type: application/json
        |Content-Length: 69
        |
        |{
        |    "name": "sample",
        |    "time": "Wed, 21 Oct 2015 18:27:50 GMT"
        |}""".stripMargin
    )
  }

  it should "render multipart form data if content is a plain string" in {
    basicRequest
      .header("Content-Type", "multipart/form-data;boundary=<PLACEHOLDER>")
      .multipartBody(multipart("k1", "v1"), multipart("k2", "v2"))
      .post(localhost).toRfc2616Format should include(
      """|Content-Disposition: form-data; name="k1"
        |
        |v1""".stripMargin
    ).and(include(
      """|Content-Disposition: form-data; name="k2"
         |
         |v2""".stripMargin
    )).and(endWith("--"))
  }

}
