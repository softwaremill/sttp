package sttp.client3.logging.slf4j

import sttp.client3._
import sttp.client3.logging.{LogLevel, LoggingBackend}
import sttp.model.HeaderNames

object Slf4jLoggingBackend {
  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      includeTiming: Boolean = true,
      beforeCurlInsteadOfShow: Boolean = false,
      logRequestBody: Boolean = false,
      logRequestHeaders: Boolean = true,
      logResponseBody: Boolean = false,
      logResponseHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
      beforeRequestSendLogLevel: LogLevel = LogLevel.Debug,
      responseLogLevel: LogLevel = LogLevel.Debug,
      responseExceptionLogLevel: LogLevel = LogLevel.Error
  ): SttpBackend[F, S] = {
    val logger = new Slf4jLogger("sttp.client3.logging.slf4j.Slf4jLoggingBackend", delegate.responseMonad)
    LoggingBackend(
      delegate,
      logger,
      includeTiming,
      beforeCurlInsteadOfShow,
      logRequestBody,
      logRequestHeaders,
      logResponseBody,
      logResponseHeaders,
      sensitiveHeaders,
      beforeRequestSendLogLevel,
      responseLogLevel,
      responseExceptionLogLevel
    )
  }
}
