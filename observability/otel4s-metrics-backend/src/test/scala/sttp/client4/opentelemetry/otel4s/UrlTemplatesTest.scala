package sttp.client4.opentelemetry.otel4s

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

class UrlTemplatesTest extends AnyFlatSpec with Matchers {

  private def replaceIds(uri: String) = UrlTemplates.replaceIds(basicRequest.get(uri"$uri"))

  it should "replace a UUID in the path" in {
    replaceIds("http://example.com/orders/550e8400-e29b-41d4-a716-446655440000") shouldBe Some(
      "/orders/{id}"
    )
  }

  it should "replace multiple IDs in the path" in {
    replaceIds("http://example.com/a/123/b/456") shouldBe Some("/a/{id}/b/{id}")
  }

  it should "replace a numeric ID in a query value" in {
    replaceIds("http://example.com/users?id=123") shouldBe Some("/users?id={id}")
  }

  it should "replace a UUID in a query value" in {
    replaceIds(
      "http://example.com/users?id=550e8400-e29b-41d4-a716-446655440000"
    ) shouldBe Some("/users?id={id}")
  }

  it should "return the same URL when there are no IDs in the query" in {
    replaceIds("http://example.com/users?active=true") shouldBe Some("/users?active=true")
  }

  it should "replace IDs in both path and query" in {
    replaceIds("http://example.com/users/42?version=7") shouldBe Some(
      "/users/{id}?version={id}"
    )
  }

  it should "return the same URL when there are no IDs in the path" in {
    replaceIds("http://example.com/users") shouldBe Some("/users")
  }

  it should "return / when the path is empty" in {
    replaceIds("http://example.com") shouldBe Some("/")
  }
}
