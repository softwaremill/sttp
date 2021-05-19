package sttp.client3

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ToCurlConverterTest extends AnyFlatSpec with Matchers with ToCurlConverterTestExtension {
  private val localhost = uri"http://localhost"

  it should "convert base request" in {
    basicRequest
      .get(uri"$localhost")
      .toCurl shouldBe "curl \\\n  --request GET \\\n  --url 'http://localhost' \\\n  --location \\\n  --max-redirs 32"
  }

  it should "convert request with method to curl" in {
    basicRequest.get(localhost).toCurl should include("--request GET")
    basicRequest.post(localhost).toCurl should include("--request POST")
    basicRequest.put(localhost).toCurl should include("--request PUT")
    basicRequest.delete(localhost).toCurl should include("--request DELETE")
    basicRequest.patch(localhost).toCurl should include("--request PATCH")
    basicRequest.head(localhost).toCurl should include("--request HEAD")
    basicRequest.options(localhost).toCurl should include("--request OPTIONS")
  }

  it should "convert request with header" in {
    basicRequest.header("User-Agent", "myapp").get(localhost).toCurl should include(
      """--header 'User-Agent: myapp'"""
    )
  }

  it should "convert request with body" in {
    basicRequest.body(Map("name" -> "john", "org" -> "sml")).post(localhost).toCurl should include(
      "--header 'Content-Type: application/x-www-form-urlencoded' \\\n  --header 'Content-Length: 17' \\\n  --form 'name=john&org=sml'"
    )
    basicRequest.body("name=john").post(localhost).toCurl should include(
      "--header 'Content-Type: text/plain; charset=utf-8' \\\n  --header 'Content-Length: 9' \\\n  --data 'name=john'"
    )
    basicRequest.body("name=john", StandardCharsets.ISO_8859_1.name()).post(localhost).toCurl should include(
      "--header 'Content-Type: text/plain; charset=ISO-8859-1' \\\n  --header 'Content-Length: 9' \\\n  --data 'name=john'"
    )
    basicRequest.body("name='john'").post(localhost).toCurl should include(
      "--header 'Content-Type: text/plain; charset=utf-8' \\\n  --header 'Content-Length: 11' \\\n  --data 'name=\\'john\\''"
    )
    basicRequest.body("name=\"john\"").post(localhost).toCurl should include(
      "--header 'Content-Type: text/plain; charset=utf-8' \\\n  --header 'Content-Length: 11' \\\n  --data 'name=\"john\"'"
    )
  }

  it should "convert request with options" in {
    basicRequest.followRedirects(false).get(localhost).toCurl should not include "--location"
    basicRequest.maxRedirects(11).get(localhost).toCurl should include("--max-redirs 11")
  }

  it should "put placeholder when sending binary data" in {
    val testBodyBytes = "this is the body".getBytes("UTF-8")

    val curl = basicRequest
      .post(localhost)
      .body(new ByteArrayInputStream(testBodyBytes))
      .toCurl
    curl should include("--data-binary <PLACEHOLDER>")
  }

  it should "render multipart form data if content is a plain string" in {
    basicRequest.multipartBody(multipart("k1", "v1"), multipart("k2", "v2")).post(localhost).toCurl should include(
      "--form 'k1=v1' \\\n  --form 'k2=v2'"
    )
  }
}
