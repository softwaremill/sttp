package sttp.model

import org.scalatest.{FlatSpec, Matchers}

class HeaderTests extends FlatSpec with Matchers {
  it should "return a string description of the header" in {
    Header.unsafeApply(HeaderNames.Authorization, "xyz").toString shouldBe "Authorization: xyz"
  }

  it should "validate status codes" in {
    Header.safeApply("Aut ho", "a bc") shouldBe 'left
    Header.safeApply(HeaderNames.Authorization, "xy z") shouldBe 'right
  }

  it should "throw exceptions on invalid headers" in {
    an[IllegalArgumentException] shouldBe thrownBy(Header.unsafeApply("Aut ho", "a bc"))
  }

  it should "create unvalidated instances" in {
    Header.notValidated("Aut ho", "a bc").toString shouldBe "Aut ho: a bc"
  }
}
