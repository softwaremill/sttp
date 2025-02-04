package sttp.client4

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.logging.{Log, LogConfig, LogLevel, Logger}
import sttp.model.{Header, StatusCode}
import sttp.shared.Identity

import scala.collection.immutable.Seq
import scala.collection.mutable
import sttp.client4.testing.ResponseStub
import sttp.client4.ResponseException.DeserializationException

class LogTests extends AnyFlatSpec with Matchers with BeforeAndAfter {
  private class SpyLogger extends Logger[Identity] {
    private val logs = mutable.ListBuffer.empty[(LogLevel, String, Option[Throwable])]
    def probe: List[(LogLevel, String, Option[Throwable])] =
      logs.toList

    def reset(): Unit =
      logs.clear()

    def apply(
        level: LogLevel,
        message: => String,
        throwable: Option[Throwable],
        context: Map[String, Any]
    ): Identity[Unit] =
      logs += ((level, message, throwable))
  }

  private val spyLogger = new SpyLogger()
  private val defaultLog = Log.default(spyLogger, LogConfig())

  before(spyLogger.reset())

  "default log" should "log before request send" in {
    defaultLog.beforeRequestSend(basicRequest.get(uri"http://example.org"))
    spyLogger.probe should be(
      List(
        (
          LogLevel.Debug,
          "Sending request: GET http://example.org, response as: either(as string, as string), headers: Accept-Encoding: gzip, deflate",
          None
        )
      )
    )
  }

  it should "log response" in {
    val request = basicRequest.get(uri"http://example.org")
    defaultLog.response(
      request = request,
      response = Response(
        body = "foo body",
        code = StatusCode.Ok,
        statusText = "Ok",
        headers = Seq(Header("Server", "sttp server")),
        history = Nil,
        request = request
      ),
      responseBody = None,
      timings = None,
      exception = None
    )
    spyLogger.probe should be(
      List(
        (
          LogLevel.Debug,
          "Request: GET http://example.org, response: 200 Ok, headers: Server: sttp server",
          None
        )
      )
    )
  }

  it should "log a DeserializationException as a response" in {
    val response = ResponseStub.ok("response body")
    val exception = new DeserializationException("response body", new RuntimeException("boom!"), response)
    defaultLog.response(
      request = basicRequest.get(uri"http://example.org"),
      response = response,
      responseBody = None,
      timings = None,
      exception = Some(exception)
    )

    spyLogger.probe should be(
      List(
        (
          LogLevel.Debug,
          "Request: GET http://example.org, response: 200 Ok, headers: ",
          Some(exception)
        )
      )
    )
  }

  it should "log request exception" in {
    val exception = new RuntimeException("test exception")
    defaultLog.requestException(
      request = basicRequest.get(uri"http://example.org"),
      timing = None,
      exception = exception
    )
    spyLogger.probe should be(
      List(
        (
          LogLevel.Error,
          "Exception when sending request: GET http://example.org",
          Some(exception)
        )
      )
    )
  }
}
