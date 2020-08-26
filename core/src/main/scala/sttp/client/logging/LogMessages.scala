package sttp.client.logging

import sttp.client.{Request, Response}

/**
  * Default log messages used by logging backend wrappers.
  */
object LogMessages {
  def beforeRequestSend(request: Request[_, _]): String =
    s"Sending request: ${request.show(includeBody = false)}"

  def response(
      request: Request[_, _],
      response: Response[_],
      withRequestBody: Boolean,
      responseBody: Option[String]
  ): String = {
    val responseAsString = response.copy(body = responseBody.getOrElse("")).show(responseBody.isDefined)
    s"For request: ${request.show(withRequestBody)}, got response: ${responseAsString}"
  }

  def requestException(request: Request[_, _], withBody: Boolean): String =
    s"Exception when sending request: ${request.show(withBody)}"

  def requestTiming(request: Request[_, _], result: String, elapsed: Long): String = {
    val elapsedStr = f"${elapsed / 1000.0}%.3fs"
    s"For request: ${request.show(includeBody = false)}, got response: $result, took: $elapsedStr"
  }

  def requestCurl(request: Request[_, _], result: String): String =
    s"Got result: $result, for request: ${request.toCurl}"
}
