package sttp.client.testing.compile

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.reflect.ToolBoxError

class IllTypedTests extends AnyFlatSpec with Matchers {
  "compilation" should "fail when trying to use websockets using the HttpURLConnectionBackend backend" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client._

        val backend = HttpURLConnectionBackend()
        basicRequest.get(uri"http://example.com").response(asWebSocketUnsafe[Identity]).send(backend)
        """)
    }

    thrown.getMessage should include(
      "Cannot prove that Any with sttp.capabilities.Effect[[X]sttp.client.Identity[X]] <:< Any with sttp.capabilities.Effect[sttp.client.Identity] with sttp.capabilities.WebSockets."
    )
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client._
        val backend = HttpURLConnectionBackend()
        basicRequest.send(backend)
        """)
    }

    thrown.getMessage should include("This is a partial request, the method & url are not specified")
  }
}
