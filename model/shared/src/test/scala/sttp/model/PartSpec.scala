package sttp.model

import org.scalatest.{FlatSpec, Matchers}

class PartSpec extends FlatSpec with Matchers{

  "headers" can "be added to part" in {
    val headerName = "X-HEADER"
    val headerValue = "X-HEADER-VALUE"
    val simple = Part("sample", "string")
    simple.header(headerName, headerValue).headers shouldBe Seq(Header.notValidated(headerName, headerValue))
  }

  "headers" can "be replaced" in {
    val headerName = "X-HEADER"
    val headerValue = "X-HEADER-VALUE"
    val headerSecondValue = "X-HEADER-VALUE-2"
    val simple = Part("sample", "string", otherHeaders = Seq(Header.notValidated(headerName, headerValue)))

    simple.header(Header.notValidated(headerName, headerSecondValue), true).headers shouldBe
      Seq(Header.notValidated(headerName, headerSecondValue))
  }

  "headers" must "be rejected" in {
    val headerName = "X-HEADER"
    val headerValue = "X-HEADER-VALUE"
    val headerSecondValue = "X-HEADER-VALUE-2"
    val simple = Part("sample", "string", otherHeaders = Seq(Header.notValidated(headerName, headerValue)))

    simple.header(Header.notValidated(headerName, headerSecondValue)).headers shouldBe
      Seq(Header.notValidated(headerName, headerValue))
  }

}
