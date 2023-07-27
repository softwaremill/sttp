package sttp.client4.testing.compile

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.reflect.ToolBoxError

class IllTypedTests extends AnyFlatSpec with Matchers {
  "compilation" should "fail when trying to use websockets using the HttpURLConnectionBackend backend" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client4._
        import sttp.client4.httpurlconnection._

        val backend = HttpURLConnectionBackend()
        basicRequest.get(uri"http://example.com").response(asWebSocketUnsafe[Identity]).send(backend)
        """)
    }

    thrown.getMessage should include("""overloaded method send with alternatives:
        |  (backend: sttp.client4.WebSocketSyncBackend)""".stripMargin)
    thrown.getMessage should include("(backend: sttp.client4.WebSocketBackend[[+X]sttp.client4.Identity[X]])")
    thrown.getMessage should include("cannot be applied to (sttp.client4.SyncBackend)")
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client4._
        import sttp.client4.httpurlconnection._

        val backend = HttpURLConnectionBackend()
        basicRequest.send(backend)
        """)
    }

    thrown.getMessage should include(
      "value send is not a member of sttp.client4.PartialRequest[Either[String,String]]"
    )
  }
}
