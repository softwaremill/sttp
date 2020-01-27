package sttp.client.logging

import sttp.client.ws.WebSocketResponse
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

  def beforeWebsocketOpen(request: Request[_, _]): String =
    s"Opening websocket: ${requestToString(request)}"

  def websocketException(request: Request[_, _]): String =
    s"Exception when opening websocket: ${requestToString(request)}"

  def websocketResponse(request: Request[_, _], response: WebSocketResponse[_]): String =
    s"For websocket request: ${requestToString(request)}, got response headers: ${headersToString(response.headers.headers)}"

  //

  def requestToString(request: Request[_, _]): String = s"${request.method} ${request.uri}"

  def responseToString(response: Response[_]): String =
    s"${response.code}, headers: ${headersToString(response.headers)}"

  def headersToString(headers: Seq[Header]): String = headers.map(_.toStringSafe).mkString(", ")
}
