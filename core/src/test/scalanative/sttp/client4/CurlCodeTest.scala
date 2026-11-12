package sttp.client4
package curl

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.curl.internal.CurlCode

class CurlCodeTest extends AnyFreeSpec with Matchers {
  "curl codes" - {
    "should use current libcurl names" in {
      CurlCode(60).toString shouldBe "PEER_FAILED_VERIFICATION"
      CurlCode(51).toString shouldBe "OBSOLETE51"
    }
  }
}
