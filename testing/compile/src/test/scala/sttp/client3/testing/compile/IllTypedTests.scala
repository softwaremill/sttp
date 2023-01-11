package sttp.client3.testing.compile

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.reflect.ToolBoxError

class IllTypedTests extends AnyFlatSpec with Matchers {
  "compilation" should "fail when trying to use websockets using the HttpURLConnectionBackend backend" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client3._

        val backend = HttpURLConnectionBackend()
        basicRequest.get(uri"http://example.com").response(asWebSocketUnsafe[Identity]).send(backend)
        """)
    }

    thrown.getMessage should include("found   : sttp.client3.SyncBackend")
    thrown.getMessage should include("required: sttp.client3.WebSocketBackend[[+X]sttp.client3.Identity[X]]")
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client3._
        val backend = HttpURLConnectionBackend()
        basicRequest.send(backend)
        """)
    }

    thrown.getMessage should include(
      "value send is not a member of sttp.client3.PartialRequest[Either[String,String]]"
    )
  }
}
