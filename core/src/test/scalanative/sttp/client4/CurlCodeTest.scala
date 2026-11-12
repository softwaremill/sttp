package sttp.client4
package curl

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.curl.internal.CurlCode

import java.io.IOException

class CurlCodeTest extends AnyFreeSpec with Matchers {
  "curl codes" - {
    "should use current libcurl names" in {
      CurlCode(60).toString shouldBe "PEER_FAILED_VERIFICATION"
      CurlCode(51).toString shouldBe "OBSOLETE51"
    }

    "should map PEER_FAILED_VERIFICATION to a plain IOException" in {
      val e = AbstractCurlBackend.transferException(60)
      e.getClass shouldBe classOf[IOException]
      e.getMessage should include("PEER_FAILED_VERIFICATION")
    }
  }
}
