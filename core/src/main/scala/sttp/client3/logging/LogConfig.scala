package sttp.client3.logging

import sttp.model.HeaderNames
import sttp.model.StatusCode

case class LogConfig(
    beforeCurlInsteadOfShow: Boolean = false,
    logRequestBody: Boolean = false,
    logRequestHeaders: Boolean = true,
    logResponseHeaders: Boolean = true,
    logResponseBody: Boolean = false,
    includeTiming: Boolean = true,
    sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
    beforeRequestSendLogLevel: LogLevel = LogLevel.Debug,
    responseLogLevel: StatusCode => LogLevel = DefaultLog.defaultResponseLogLevel,
    responseExceptionLogLevel: LogLevel = LogLevel.Error
)

object LogConfig {
  val Default: LogConfig = LogConfig()
}
