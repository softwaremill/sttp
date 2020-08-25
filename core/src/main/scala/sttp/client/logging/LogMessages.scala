package sttp.client.logging

import sttp.client.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  InputStreamBody,
  MultipartBody,
  NoBody,
  Request,
  Response,
  StreamBody,
  StringBody
}
import sttp.model.Header

/**
  * Default log messages used by logging backend wrappers.
  */
object LogMessages {
  def beforeRequestSend(request: Request[_, _]): String =
    s"Sending request: ${requestToString(request, withBody = false)}"

  def response(
      request: Request[_, _],
      response: Response[_],
      withRequestBody: Boolean,
      responseBody: Option[String]
  ): String =
    s"For request: ${requestToString(request, withRequestBody)}, got response: ${responseToString(response, responseBody)}"

  def requestException(request: Request[_, _], withBody: Boolean): String =
    s"Exception when sending request: ${requestToString(request, withBody)}"

  def requestTiming(request: Request[_, _], result: String, elapsed: Long): String = {
    val elapsedStr = f"${elapsed / 1000.0}%.3fs"
    s"For request: ${requestToString(request, withBody = false)}, got response: $result, took: $elapsedStr"
  }

  def requestCurl(request: Request[_, _], result: String): String =
    s"Got result: $result, for request: ${request.toCurl}"

  //

  def requestToString(request: Request[_, _], withBody: Boolean): String = {
    val ws = if (request.isWebSocket) " (WebSocket) " else ""
    val body = if (withBody) " " + requestBodyToString(request) else ""
    s"${request.method}$ws ${request.uri}$body"
  }

  def responseToString(response: Response[_], responseBody: Option[String]): String =
    s"${response.code}, headers: ${headersToString(response.headers)}${responseBody.map(", body: " + _).getOrElse("")}"

  def headersToString(headers: Seq[Header]): String = headers.map(_.toStringSafe).mkString(", ")

  //

  def requestBodyToString(request: Request[_, _]): String =
    request.body match {
      case NoBody                => "(empty body)"
      case StringBody(s, _, _)   => s
      case ByteArrayBody(_, _)   => "(byte array)"
      case ByteBufferBody(_, _)  => "(byte buffer)"
      case InputStreamBody(_, _) => "(input stream)"
      case FileBody(f, _)        => s"(file ${f.name})"
      case StreamBody(_)         => "(stream)"
      case MultipartBody(parts)  => s"(multipart: ${parts.map(p => p.name).mkString(",")})"
    }
}
