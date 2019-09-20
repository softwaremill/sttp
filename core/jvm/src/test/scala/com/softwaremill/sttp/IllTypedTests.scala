package com.softwaremill.sttp

import com.softwaremill.sttp.testing.EvalScala
import org.scalatest.{FlatSpec, Matchers}

import scala.tools.reflect.ToolBoxError

class IllTypedTests extends FlatSpec with Matchers {
  "compilation" should "fail when trying to stream using the default backend" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import com.softwaremill.sttp._

        class MyStream[T]()

        implicit val sttpBackend = HttpURLConnectionBackend()
        request.get(uri"http://example.com").response(asStream[MyStream[Byte]]).send()
        """)
    }

    thrown.getMessage should include(
      "could not find implicit value for parameter backend: com.softwaremill.sttp.SttpBackend[R,MyStream[Byte]]"
    )
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import com.softwaremill.sttp._
        implicit val sttpBackend = HttpURLConnectionBackend()
        request.send()
        """)
    }

    thrown.getMessage should include("This is a partial request, the method & url are not specified")
  }
}
