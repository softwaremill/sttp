package com.softwaremill.sttp

import org.scalatest.{FlatSpec, Matchers}

class IllTypedTests extends FlatSpec with Matchers {
  "compilation" should "fail when trying to stream using the default handler" in {
    """
    import akka.stream.scaladsl.Source
    import akka.util.ByteString
    import java.net.URI
    implicit val sttpHandler = HttpURLConnectionSttpHandler
    sttp.get(new URI("http://example.com")).response(asStream[Source[ByteString, Any]]).send()
    """ shouldNot typeCheck
  }

  "compilation" should "fail when trying to send a request without giving an URL" in {
    """
    import java.net.URI
    implicit val sttpHandler = HttpURLConnectionSttpHandler
    sttp.send()
    """ shouldNot typeCheck
  }
}
