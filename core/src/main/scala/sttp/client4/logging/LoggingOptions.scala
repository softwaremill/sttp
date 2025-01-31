package sttp.client4.logging

/** Logging configuration that can be set for individual requests, overriding what's set in [[LogConfig]] when creating
  * the log backend.
  */
case class LoggingOptions(
    log: Boolean = true,
    includeTimings: Option[Boolean] = None,
    logRequestBody: Option[Boolean] = None,
    logResponseBody: Option[Boolean] = None,
    logRequestHeaders: Option[Boolean] = None,
    logResponseHeaders: Option[Boolean] = None
)
