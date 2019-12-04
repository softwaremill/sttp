package sttp.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StatusCodeTests extends AnyFlatSpec with Matchers {
  it should "return a string description of the status code" in {
    StatusCode.Accepted.toString shouldBe "202"
  }

  it should "validate status codes" in {
    StatusCode.safeApply(8) shouldBe 'left
    StatusCode.safeApply(200) shouldBe Right(StatusCode.Ok)
  }

  it should "throw exceptions on invalid status codes" in {
    an[IllegalArgumentException] shouldBe thrownBy(StatusCode.unsafeApply(8))
  }
}
