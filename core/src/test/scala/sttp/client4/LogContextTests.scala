package sttp.client4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.logging.LogContext
import sttp.model.{Header, HeaderNames, StatusCode}

import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt

class LogContextTests extends AnyFlatSpec with Matchers {

  "default log context of request" should "be provide full context by default" in {
    val defaultLogContext = LogContext.default()
    defaultLogContext.forRequest(basicRequest.get(uri"http://example.org").auth.bearer("token")) should be(
      Map(
        "http.request.uri" -> "http://example.org",
        "http.request.method" -> "GET",
        "http.request.headers" -> "Accept-Encoding: gzip, deflate | Authorization: ***"
      )
    )
  }

  it should "be provide context without headers according to settings" in {
    val defaultLogContext = LogContext.default(logRequestHeaders = false)
    defaultLogContext.forRequest(basicRequest.get(uri"http://example.org")) should be(
      Map(
        "http.request.uri" -> "http://example.org",
        "http.request.method" -> "GET"
      )
    )
  }

  "default log context of response" should "be provide full context by default" in {
    val defaultLogContext = LogContext.default()
    val response: Response[String] = Response(
      body = "foo body",
      code = StatusCode.Ok,
      statusText = "Ok",
      headers = Seq(Header(HeaderNames.Server, "sttp server")),
      history = Nil,
      request = basicRequest.get(uri"http://example.org").auth.bearer("token")
    )

    defaultLogContext.forResponse(response, Some(1234.millis)) should be(
      Map(
        "http.request.uri" -> "http://example.org",
        "http.request.method" -> "GET",
        "http.request.headers" -> "Accept-Encoding: gzip, deflate | Authorization: ***",
        "http.response.headers" -> "Server: sttp server",
        "http.response.status_code" -> 200,
        "http.duration" -> 1234000000
      )
    )
  }

  it should "be provide context without headers according to settings" in {
    val defaultLogContext = LogContext.default(logResponseHeaders = false, logRequestHeaders = false)
    val response: Response[String] = Response(
      body = "foo body",
      code = StatusCode.Ok,
      statusText = "Ok",
      headers = Seq(Header(HeaderNames.Server, "sttp server")),
      history = Nil,
      request = basicRequest.get(uri"http://example.org").auth.bearer("token")
    )

    defaultLogContext.forResponse(response, Some(1234.millis)) should be(
      Map(
        "http.request.uri" -> "http://example.org",
        "http.request.method" -> "GET",
        "http.response.status_code" -> 200,
        "http.duration" -> 1234000000
      )
    )
  }
}
