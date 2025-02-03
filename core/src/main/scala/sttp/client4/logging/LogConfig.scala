package sttp.client4.logging

import sttp.model.HeaderNames
import sttp.model.StatusCode

/** Configuration used to create log messages, to be provided to a [[LoggingBackend]]. */
case class LogConfig(
    /** When [[logRequestBody]] and [[logRequestHeaders]] are `true`, should the log contain a CURL command to reproduce
      * sending the request, instead of using [[sttp.client4.Request.show]].
      */
    beforeCurlInsteadOfShow: Boolean = false,
    /** Should the request body be included in the log message that is logged before sending a request (is possible). */
    logRequestBody: Boolean = false,
    /** Should the non-sensitive request headers be included in the log message that is logged before sending a request.
      */
    logRequestHeaders: Boolean = true,
    /** Should the non-sensitive response headers be included in the log message that is logged after receiving a
      * response.
      */
    logResponseHeaders: Boolean = true,
    /** Should the response body be included in the log message that is logged after receiving a response. The response
      * body will only be logged if the response description for the request allows it (e.g. `asString` allows logging
      * the response body, `asInputStream` does not). Moreover, the response body will not be logged if processing the
      * response (even if it's parsing of a successfully received body) throws an exception.
      */
    logResponseBody: Boolean = false,
    /** Should the time it takes to complete the request be included in the log message that is logged after receiving a
      * response, or when an exception occurs.
      *
      * Two durations are included: one from the start of the request until the response body is fully received, and
      * another one when the response body is fully handled (e.g. including parsing).
      */
    includeTimings: Boolean = true,
    /** The sensitive headers that are filtered out, when logging request & response headers. */
    sensitiveHeaders: Set[String] = HeaderNames.SensitiveHeaders,
    /** The log level that is used for the log message, that is being logged before sending a request. */
    beforeRequestSendLogLevel: LogLevel = LogLevel.Debug,
    /** The log level that is used for the log message, that is being logged after receiving a response, depending on
      * the status code.
      */
    responseLogLevel: StatusCode => LogLevel = { (c: StatusCode) =>
      if (c.isClientError) LogLevel.Error else { if (c.isServerError) LogLevel.Warn else LogLevel.Debug }
    },
    /** The log level that is used for the log message, that is being logged when an exception occurs during sending of
      * a request.
      */
    responseExceptionLogLevel: LogLevel = LogLevel.Error
)

object LogConfig {
  val Default: LogConfig = LogConfig()
}
