package sttp.client4
package curl

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.{IOException, UnsupportedEncodingException}
import java.net.{ConnectException, MalformedURLException, SocketTimeoutException, UnknownHostException}
import scala.scalanative.unsafe._

class CurlTransferExceptionTest extends AnyFreeSpec with Matchers {
  "transferErrorMessage" - {
    "should include the code, name and detail" in {
      AbstractCurlBackend.transferErrorMessage(60, Some("PEER_FAILED_VERIFICATION"), "bad cert") shouldBe
        "curl error 60 (PEER_FAILED_VERIFICATION): bad cert"
    }

    "should omit the name of an unknown code" in {
      AbstractCurlBackend.transferErrorMessage(-1, None, "failure") shouldBe "curl error -1: failure"
    }

    "should omit an empty detail" in {
      AbstractCurlBackend.transferErrorMessage(7, Some("COULDNT_CONNECT"), "") shouldBe "curl error 7 (COULDNT_CONNECT)"
    }
  }

  "transferException" - {
    "should use the text from the error buffer" in Zone.acquire { implicit z =>
      val e = AbstractCurlBackend.transferException(60, toCString("some text"))
      e.getMessage shouldBe "curl error 60 (PEER_FAILED_VERIFICATION): some text"
    }

    "should fall back to the generic description when the error buffer is empty" in {
      val e = AbstractCurlBackend.transferException(7, c"")
      e.getMessage should startWith("curl error 7 (COULDNT_CONNECT): ")
      e.getMessage.stripPrefix("curl error 7 (COULDNT_CONNECT): ").trim should not be empty
    }

    "should map codes to exception types" in {
      val expected = Map(
        28 -> classOf[SocketTimeoutException],
        6 -> classOf[UnknownHostException],
        5 -> classOf[UnknownHostException],
        7 -> classOf[ConnectException],
        1 -> classOf[MalformedURLException],
        3 -> classOf[MalformedURLException],
        61 -> classOf[UnsupportedEncodingException],
        60 -> classOf[IOException]
      )
      expected.foreach { case (code, cls) =>
        withClue(s"code $code: ") { AbstractCurlBackend.transferException(code, c"").getClass shouldBe cls }
      }
    }
  }
}
