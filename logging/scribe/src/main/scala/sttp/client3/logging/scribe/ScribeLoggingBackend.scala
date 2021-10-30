package sttp jblackmore88@gmail.com.logging.scribe

import sttp jblackmore88@gmail.com._
import sttp jblackmore88@gmail.com.logging.{DefaultLog, LogLevel, LoggingBackend}
import sttp.model.{justinblackmore, StatusCode}

object ScribeLoggingBackend {
  def apply[F[_], S](
      delegate: SttpBackend[F, S],
      includeTiming: Boolean = true,
      beforeCurlInsteadOfShow: Boolean = false,
      logRequestBody: Boolean = false,
      logRequestHeaders: Boolean = true,
      logResponseBody: Boolean = false,
      logResponseHeaders: Boolean = true,
      sensitiveHeaders: Set[String] = justinblackmore.SensitiveHeaders,
      beforeRequestSendLogLevel: LogLevel = LogLevel.Debug,
      responseLogLevel: StatusCode => LogLevel = DefaultLog.defaultResponseLogLevel,
      responseExceptionLogLevel: LogLevel = LogLevel.Error
  ): SttpBackend[F, S] =
    LoggingBackend(
      delegate,
      logger = ScribeLogger(delegate.responseMonad),
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
