package sttp.client4.logging

import sttp.client4.Response
import sttp.model.{HeaderNames, RequestMetadata}

import scala.collection.mutable
import scala.concurrent.duration.Duration

trait LogContext {
  def ofRequest(request: RequestMetadata): Map[String, Any]

  def ofResponse(response: Response[_], duration: Option[Duration]): Map[String, Any]
}

object LogContext {

  def noop: LogContext = new LogContext {
    def ofRequest(request: RequestMetadata): Map[String, Any] = Map.empty
    def ofResponse(response: Response[_], duration: Option[Duration]): Map[String, Any] = Map.empty
  }

  def default(
      logRequestHeaders: Boolean = true,
      logResponseHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders
  ): LogContext = new LogContext {
    def ofRequest(request: RequestMetadata): Map[String, Any] = {
      val context = mutable.Map.empty[String, Any]

      context += "http.request.method" -> request.method.toString
      context += "http.request.uri" -> request.uri.toString
      if (logRequestHeaders)
        context += "http.request.headers" -> request.headers.map(_.toStringSafe(sensitiveHeaders)).mkString(" | ")

      context.toMap
    }

    def ofResponse(response: Response[_], duration: Option[Duration]): Map[String, Any] = {
      val context = mutable.Map.empty[String, Any]

      context ++= ofRequest(response.request)
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
