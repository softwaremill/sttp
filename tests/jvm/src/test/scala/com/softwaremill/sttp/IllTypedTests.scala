package com.softwaremill.sttp

import org.scalatest.{FlatSpec, Matchers}

import scala.tools.reflect.ToolBoxError

class IllTypedTests extends FlatSpec with Matchers {
  "compilation" should "fail when trying to stream using the default backend" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import com.softwaremill.sttp._
        import akka.stream.scaladsl.Source
        import akka.util.ByteString
        implicit val sttpBackend = HttpURLConnectionBackend()
        sttp.get(uri"http://example.com").response(asStream[Source[ByteString, Any]]).send()
        """)
    }

    thrown.getMessage should include(
      "could not find implicit value for parameter backend: com.softwaremill.sttp.SttpBackend[R,akka.stream.scaladsl.Source[akka.util.ByteString,Any]]")
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    val thrown = intercept[ToolBoxError] {
      EvalScala("""
        import com.softwaremill.sttp._
        implicit val sttpBackend = HttpURLConnectionBackend()
        sttp.send()
        """)
    }

    thrown.getMessage should include("This is a partial request, the method & url are not specified")
  }
}
