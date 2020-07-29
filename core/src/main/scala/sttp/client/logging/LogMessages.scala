package sttp.client.logging

import sttp.client.{Request, Response}
import sttp.model.Header

/**
  * Default log messages used by logging backend wrappers.
  */
object LogMessages {
  def beforeRequestSend(request: Request[_, _]): String =
    s"Sending request: ${requestToString(request)}"

  def response(request: Request[_, _], response: Response[_]): String =
    s"For request: ${requestToString(request)}, got response: ${responseToString(response)}"

  def requestException(request: Request[_, _]): String =
    s"Exception when sending request: ${requestToString(request)}"

  def requestTiming(request: Request[_, _], result: String, elapsed: Long): String = {
    val elapsedStr = f"${elapsed / 1000.0}%.3fs"
    s"For request: ${requestToString(request)}, got response: $result, took: $elapsedStr"
  }

  def requestCurl(request: Request[_, _], result: String): String =
    s"Got result: $result, for request: ${request.toCurl}"

  //

  def requestToString(request: Request[_, _]): String = {
    val ws = if (request.isWebSocket) " (WebSocket) " else ""
    s"${request.method}$ws ${request.uri}"
  }

  def responseToString(response: Response[_]): String =
    s"${response.code}, headers: ${headersToString(response.headers)}"

  def headersToString(headers: Seq[Header]): String = headers.map(_.toStringSafe).mkString(", ")
}
