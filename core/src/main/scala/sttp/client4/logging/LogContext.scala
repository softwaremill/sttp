package sttp.client4.logging

import sttp.model.{HeaderNames, RequestMetadata}

import scala.collection.mutable
import sttp.model.ResponseMetadata

trait LogContext {
  def forRequest(request: RequestMetadata): Map[String, Any]
  def forResponse(
      request: RequestMetadata,
      response: ResponseMetadata,
      timings: Option[ResponseTimings]
  ): Map[String, Any]
}

object LogContext {

  /** An empty log context.
    */
  def empty: LogContext = new LogContext {
    def forRequest(request: RequestMetadata): Map[String, Any] = Map.empty
    def forResponse(
        request: RequestMetadata,
        response: ResponseMetadata,
        timings: Option[ResponseTimings]
    ): Map[String, Any] = Map.empty
  }

  /** Default log context, which logs the main request and response metadata.
    * @param logRequestHeaders
    *   Whether to log request headers.
    * @param logResponseHeaders
    *   Whether to log response headers.
    * @param sensitiveHeaders
    *   Headers which should not be logged.
    * @param sensitiveQueryParams
    *   URI query params which should not be logged.
    */
  def default(
      logRequestHeaders: Boolean = true,
      logResponseHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
      sensitiveQueryParams: Set[String] = Set.empty
  ): LogContext = new LogContext {
    def forRequest(request: RequestMetadata): Map[String, Any] = {
      val context = mutable.Map.empty[String, Any]

      context += "http.request.method" -> request.method.toString
      context += "http.request.uri" -> request.uri.toStringSafe(sensitiveQueryParams)
      if (logRequestHeaders)
        context += "http.request.headers" -> request.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(" | ")

      context.toMap
    }

    def forResponse(
        request: RequestMetadata,
        response: ResponseMetadata,
        timings: Option[ResponseTimings]
    ): Map[String, Any] = {
      val context = mutable.Map.empty[String, Any]

      context ++= forRequest(request)
      context += "http.response.status_code" -> response.code.code

      if (logResponseHeaders)
        context += "http.response.headers" -> response.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(" | ")
      timings.foreach { t =>
        context += "http.duration" -> t.bodyReceived.getOrElse(t.bodyHandled).toNanos
      }

      context.toMap
    }
  }
}
