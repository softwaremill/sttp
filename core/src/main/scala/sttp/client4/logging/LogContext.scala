package sttp.client4.logging

import sttp.model.{HeaderNames, RequestMetadata}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import sttp.model.ResponseMetadata

trait LogContext {
  def forRequest(request: RequestMetadata): Map[String, Any]
  def forResponse(request: RequestMetadata, response: ResponseMetadata, duration: Option[Duration]): Map[String, Any]
}

object LogContext {

  /** An empty log context.
    */
  def empty: LogContext = new LogContext {
    def forRequest(request: RequestMetadata): Map[String, Any] = Map.empty
    def forResponse(
        request: RequestMetadata,
        response: ResponseMetadata,
        duration: Option[Duration]
    ): Map[String, Any] = Map.empty
  }

  /** Default log context, which logs the main request and response metadata.
    * @param logRequestHeaders
    *   Whether to log request headers.
    * @param logResponseHeaders
    *   Whether to log response headers.
    * @param sensitiveHeaders
    *   Headers which should not be logged.
    */
  def default(
      logRequestHeaders: Boolean = true,
      logResponseHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): LogContext = new LogContext {
    def forRequest(request: RequestMetadata): Map[String, Any] = {
      val context = mutable.Map.empty[String, Any]

      context += "http.request.method" -> request.method.toString
      context += "http.request.uri" -> request.uri.toString
      if (logRequestHeaders)
        context += "http.request.headers" -> request.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(" | ")

      context.toMap
    }

    def forResponse(
        request: RequestMetadata,
        response: ResponseMetadata,
        duration: Option[Duration]
    ): Map[String, Any] = {
      val context = mutable.Map.empty[String, Any]

      context ++= forRequest(request)
      context += "http.response.status_code" -> response.code.code

      if (logResponseHeaders)
        context += "http.response.headers" -> response.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(" | ")
      duration.foreach {
        context += "http.duration" -> _.toNanos
      }

      context.toMap
    }
  }
}
