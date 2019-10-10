package sttp.client

import org.scalatest.{FlatSpec, Matchers}
import sttp.client.testing.EvalScala

import scala.tools.reflect.ToolBoxError

class IllTypedTests extends FlatSpec with Matchers {
  "compilation" should "fail when trying to stream using the default backend" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client._

        class MyStream[T]()

        implicit val sttpBackend = HttpURLConnectionBackend()
        basicRequest.get(uri"http://example.com").response(asStream[MyStream[Byte]]).send()
        """)
    }

    thrown.getMessage should include(
      "could not find implicit value for parameter backend: sttp.client.SttpBackend[R,MyStream[Byte],sttp.client.NothingT]"
    )
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import sttp.client._
        implicit val sttpBackend = HttpURLConnectionBackend()
        basicRequest.send()
        """)
    }

    thrown.getMessage should include("This is a partial request, the method & url are not specified")
  }
}
